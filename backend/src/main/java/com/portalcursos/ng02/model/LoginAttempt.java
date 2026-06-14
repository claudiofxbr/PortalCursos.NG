package com.portalcursos.ng02.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "login_attempts")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LoginAttempt {

    @Id
    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "blocked_until")
    private Instant blockedUntil;

    @Column(name = "last_attempt", nullable = false)
    private Instant lastAttempt;
}
