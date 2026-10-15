package com.portalcursos.tokencontrol.service;

import com.portalcursos.tokencontrol.dto.IngestResult;
import com.portalcursos.tokencontrol.dto.UsageEntryRequest;
import com.portalcursos.tokencontrol.repository.UsageRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/** Ingestão idempotente: reenviar o mesmo lote (ou mensagens já gravadas) nunca duplica consumo. */
@Service
public class IngestService {

    private static final String INSERT = """
        insert into token_usage_entries (message_id, occurred_at, hour_bucket, session_id, process, model,
            input_tokens, output_tokens, cache_creation_tokens, cache_read_tokens, created_at)
        values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""";

    // Mensagem sincronizada no meio do streaming fica com output parcial; reenvio mais completo (output maior) corrige.
    private static final String REFRESH = """
        update token_usage_entries
           set input_tokens = ?, output_tokens = ?, cache_creation_tokens = ?, cache_read_tokens = ?
         where message_id = ? and output_tokens < ?""";

    private final UsageRepository repository;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final Clock clock;

    public IngestService(UsageRepository repository, JdbcTemplate jdbc, TransactionTemplate tx, Clock clock) {
        this.repository = repository;
        this.jdbc = jdbc;
        this.tx = tx;
        this.clock = clock;
    }

    public IngestResult ingest(List<UsageEntryRequest> requests) {
        // dedupe dentro do próprio lote (primeira ocorrência vence)
        Map<String, UsageEntryRequest> unique = new LinkedHashMap<>();
        for (UsageEntryRequest r : requests) {
            unique.putIfAbsent(r.messageId(), r);
        }
        int inserted;
        int updated;
        try {
            Set<String> existing = repository.findExistingMessageIds(unique.keySet());
            inserted = insertMissing(unique, existing);
            updated = refreshExisting(unique, existing);
        } catch (DataIntegrityViolationException race) {
            // outro collector gravou as mesmas mensagens entre o SELECT e o INSERT — reavalia uma vez
            Set<String> existing = repository.findExistingMessageIds(unique.keySet());
            inserted = insertMissing(unique, existing);
            updated = refreshExisting(unique, existing);
        }
        touchLastIngest();
        return new IngestResult(requests.size(), inserted, requests.size() - inserted, updated);
    }

    /** Marca a sincronização (mesmo sem linhas novas): é o que a Torre usa para saber se o collector está ativo. */
    private void touchLastIngest() {
        jdbc.update("update token_plan_config set last_ingest_at = ? where id = 1", OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC));
    }

    /**
     * Inserção em LOTE (um round-trip, com reWriteBatchedInserts no pgjdbc), numa única transação.
     * Antes era um INSERT por mensagem (IDENTITY desliga o batch do Hibernate): com ~120 ms de latência até o
     * Neon, 500 mensagens levavam ~60 s e estouravam o timeout do BFF (502).
     */
    private int insertMissing(Map<String, UsageEntryRequest> unique, Set<String> existing) {
        OffsetDateTime now = OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
        List<UsageEntryRequest> toSave = unique.values().stream().filter(r -> !existing.contains(r.messageId())).toList();
        if (toSave.isEmpty()) {
            return 0;
        }
        tx.executeWithoutResult(status -> jdbc.batchUpdate(INSERT, toSave, toSave.size(), (ps, r) -> {
            ps.setString(1, r.messageId());
            ps.setObject(2, OffsetDateTime.ofInstant(r.occurredAt(), ZoneOffset.UTC));
            ps.setObject(3, OffsetDateTime.ofInstant(r.occurredAt().truncatedTo(ChronoUnit.HOURS), ZoneOffset.UTC));
            ps.setString(4, r.sessionId());
            ps.setString(5, r.process().trim());
            ps.setString(6, r.model().trim());
            ps.setLong(7, r.inputTokens());
            ps.setLong(8, r.outputTokens());
            ps.setLong(9, r.cacheCreationTokens());
            ps.setLong(10, r.cacheReadTokens());
            ps.setObject(11, now);
        }));
        return toSave.size();
    }

    /** Corrige mensagens já gravadas com contagem parcial (output menor que o reenviado). Retorna quantas mudaram. */
    private int refreshExisting(Map<String, UsageEntryRequest> unique, Set<String> existing) {
        List<UsageEntryRequest> candidates = unique.values().stream().filter(r -> existing.contains(r.messageId())).toList();
        if (candidates.isEmpty()) {
            return 0;
        }
        int[][] counts = tx.execute(status -> jdbc.batchUpdate(REFRESH, candidates, candidates.size(), (ps, r) -> {
            ps.setLong(1, r.inputTokens());
            ps.setLong(2, r.outputTokens());
            ps.setLong(3, r.cacheCreationTokens());
            ps.setLong(4, r.cacheReadTokens());
            ps.setString(5, r.messageId());
            ps.setLong(6, r.outputTokens());
        }));
        int changed = 0;
        for (int[] batch : counts) {
            for (int c : batch) {
                changed += Math.max(c, 0); // SUCCESS_NO_INFO (-2) não conta
            }
        }
        return changed;
    }
}
