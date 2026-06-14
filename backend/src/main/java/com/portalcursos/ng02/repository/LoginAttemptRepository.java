package com.portalcursos.ng02.repository;

import com.portalcursos.ng02.model.LoginAttempt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Repository
public interface LoginAttemptRepository extends JpaRepository<LoginAttempt, String> {

    /** Remove registros de IPs cujo bloqueio expirou há mais de 24h (limpeza periódica). */
    @Modifying
    @Transactional
    @Query("DELETE FROM LoginAttempt la WHERE la.blockedUntil IS NOT NULL AND la.blockedUntil < :cutoff")
    void deleteExpiredBefore(Instant cutoff);
}
