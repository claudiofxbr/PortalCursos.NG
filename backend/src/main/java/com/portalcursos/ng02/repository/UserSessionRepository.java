package com.portalcursos.ng02.repository;

import com.portalcursos.ng02.model.User;
import com.portalcursos.ng02.model.UserSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Repository
public interface UserSessionRepository extends JpaRepository<UserSession, Long> {
    
    Optional<UserSession> findByRefreshToken(String refreshToken);

    @Modifying
    @Transactional
    int deleteByUser(User user);

    @Modifying
    @Transactional
    void deleteByRefreshToken(String refreshToken);

    @Modifying
    @Transactional
    @Query("DELETE FROM UserSession s WHERE s.expiryDate < CURRENT_TIMESTAMP")
    int deleteExpiredSessions();
}
