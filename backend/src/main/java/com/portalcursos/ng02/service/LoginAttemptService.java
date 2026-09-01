package com.portalcursos.ng02.service;

import com.portalcursos.ng02.model.LoginAttempt;
import com.portalcursos.ng02.repository.LoginAttemptRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * Proteção contra força bruta baseada em banco de dados.
 * Persiste tentativas de login por IP — sobrevive a restarts e funciona em múltiplas instâncias.
 */
@Service
@RequiredArgsConstructor
public class LoginAttemptService {
    private static final Logger logger = LoggerFactory.getLogger(LoginAttemptService.class);

    private static final int MAX_ATTEMPTS = 5;
    private static final int BLOCK_MINUTES = 15;

    private final LoginAttemptRepository loginAttemptRepository;

    // Auto-injeção via proxy (self) para que as chamadas abaixo passem pelo AOP do Spring e
    // cada uma abra sua própria transação (chamada direta via "this." ignora o proxy).
    @Lazy
    @Autowired
    private LoginAttemptService self;

    @Transactional
    public void loginSucceeded(String ip) {
        if (ip == null || ip.isBlank()) return;
        loginAttemptRepository.deleteById(ip);
        logger.debug("[SECURITY] Login bem-sucedido — tentativas resetadas para IP: {}", ip);
    }

    /**
     * Deliberadamente NÃO transacional: delega a duas transações SEQUENCIAIS (não aninhadas)
     * para que cada uma use e devolva sua própria conexão ao pool antes da próxima abrir —
     * o pool do Neon é propositalmente pequeno (maximum-pool-size=5) e não sobra margem para
     * manter 2 conexões presas por requisição de login (o que aconteceria se ensureRecordExists
     * fosse chamado com REQUIRES_NEW de dentro de uma transação já aberta).
     */
    public void loginFailed(String ip) {
        if (ip == null || ip.isBlank()) return;
        try {
            self.ensureRecordExists(ip);
        } catch (DataIntegrityViolationException e) {
            // Outra requisição concorrente já inseriu o registro para este IP — ok, segue.
            // O catch precisa ficar FORA da transação de ensureRecordExists: uma vez que o
            // flush falha por violação de constraint, o JPA marca a transação para rollback
            // (mesmo que a exceção seja capturada internamente, o commit subsequente falharia
            // com UnexpectedRollbackException). Deixando a exceção propagar, o proxy
            // @Transactional faz o rollback limpo e devolve a conexão ao pool antes de chegar
            // aqui.
        }
        self.incrementAttemptsAndMaybeBlock(ip);
    }

    /**
     * Garante a existência do registro antes do lock pessimista em incrementAttemptsAndMaybeBlock():
     * SELECT ... FOR UPDATE só serializa acesso a uma linha que já existe, não protege contra
     * duas requisições tentando inserir a mesma PK pela primeira vez ao mesmo tempo.
     */
    @Transactional
    public void ensureRecordExists(String ip) {
        if (loginAttemptRepository.existsById(ip)) return;
        loginAttemptRepository.saveAndFlush(LoginAttempt.builder()
                .ipAddress(ip)
                .attempts(0)
                .lastAttempt(Instant.now())
                .build());
    }

    @Transactional
    public void incrementAttemptsAndMaybeBlock(String ip) {
        LoginAttempt record = loginAttemptRepository.findByIpForUpdate(ip)
                .orElseThrow(() -> new IllegalStateException(
                        "Registro de tentativas de login não encontrado para IP após ensureRecordExists: " + ip));

        record.setAttempts(record.getAttempts() + 1);
        record.setLastAttempt(Instant.now());

        logger.warn("[SECURITY] Falha de login para IP: {}. Tentativa: {}/{}", ip, record.getAttempts(), MAX_ATTEMPTS);

        if (record.getAttempts() >= MAX_ATTEMPTS && record.getBlockedUntil() == null) {
            Instant unblockAt = Instant.now().plus(BLOCK_MINUTES, ChronoUnit.MINUTES);
            record.setBlockedUntil(unblockAt);
            logger.error("[SECURITY] IP BLOQUEADO por força bruta: {}. Desbloqueio em: {}", ip, unblockAt);
        }

        loginAttemptRepository.save(record);
    }

    @Transactional
    public boolean isBlocked(String ip) {
        if (ip == null || ip.isBlank()) return false;
        return loginAttemptRepository.findById(ip)
                .map(record -> {
                    if (record.getBlockedUntil() == null) return false;

                    if (record.getBlockedUntil().isBefore(Instant.now())) {
                        // Bloqueio expirou — limpa o registro
                        loginAttemptRepository.delete(record);
                        logger.info("[SECURITY] IP desbloqueado automaticamente: {}", ip);
                        return false;
                    }

                    return true;
                })
                .orElse(false);
    }
}
