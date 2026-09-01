package com.portalcursos.ng02.repository;

import com.portalcursos.ng02.model.LoginAttempt;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

@Repository
public interface LoginAttemptRepository extends JpaRepository<LoginAttempt, String> {

    /** Remove registros de IPs cujo bloqueio expirou há mais de 24h (limpeza periódica). */
    @Modifying
    @Transactional
    @Query("DELETE FROM LoginAttempt la WHERE la.blockedUntil IS NOT NULL AND la.blockedUntil < :cutoff")
    void deleteExpiredBefore(Instant cutoff);

    /**
     * SELECT ... FOR UPDATE — usado por LoginAttemptService#incrementAttemptsAndMaybeBlock()
     * para serializar leitura+incremento do contador entre requisições concorrentes do mesmo
     * IP e evitar lost update (contador subestimado / bloqueio nunca acionado sob concorrência).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT la FROM LoginAttempt la WHERE la.ipAddress = :ip")
    Optional<LoginAttempt> findByIpForUpdate(@Param("ip") String ip);
}
