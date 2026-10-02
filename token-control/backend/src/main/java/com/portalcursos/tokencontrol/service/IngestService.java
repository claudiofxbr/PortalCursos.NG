package com.portalcursos.tokencontrol.service;

import com.portalcursos.tokencontrol.dto.IngestResult;
import com.portalcursos.tokencontrol.dto.UsageEntryRequest;
import com.portalcursos.tokencontrol.model.UsageEntry;
import com.portalcursos.tokencontrol.repository.UsageRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

/** Ingestão idempotente: reenviar o mesmo lote (ou mensagens já gravadas) nunca duplica consumo. */
@Service
public class IngestService {

    private final UsageRepository repository;
    private final Clock clock;

    public IngestService(UsageRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    public IngestResult ingest(List<UsageEntryRequest> requests) {
        // dedupe dentro do próprio lote (primeira ocorrência vence)
        Map<String, UsageEntryRequest> unique = new LinkedHashMap<>();
        for (UsageEntryRequest r : requests) {
            unique.putIfAbsent(r.messageId(), r);
        }
        int inserted;
        try {
            inserted = insertMissing(unique);
        } catch (DataIntegrityViolationException race) {
            // outro collector gravou as mesmas mensagens entre o SELECT e o INSERT — reavalia uma vez
            inserted = insertMissing(unique);
        }
        return new IngestResult(requests.size(), inserted, requests.size() - inserted);
    }

    private int insertMissing(Map<String, UsageEntryRequest> unique) {
        Set<String> existing = repository.findExistingMessageIds(unique.keySet());
        Instant now = clock.instant();
        List<UsageEntry> toSave = unique.values().stream()
                .filter(r -> !existing.contains(r.messageId()))
                .map(r -> new UsageEntry(r.messageId(), r.occurredAt(), r.occurredAt().truncatedTo(ChronoUnit.HOURS),
                        r.sessionId(), r.process().trim(), r.model().trim(), r.inputTokens(), r.outputTokens(),
                        r.cacheCreationTokens(), r.cacheReadTokens(), now))
                .toList();
        repository.saveAll(toSave);
        return toSave.size();
    }
}
