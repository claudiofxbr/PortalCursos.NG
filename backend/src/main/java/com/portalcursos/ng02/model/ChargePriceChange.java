package com.portalcursos.ng02.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Proposta de novo valor feita por um chefe de secretaria; só um administrador a torna vigente. */
@Entity
@Table(name = "charge_price_changes")
@Getter
@Setter
@NoArgsConstructor
public class ChargePriceChange {

    public enum Status { PENDING, APPROVED, REJECTED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "charge_price_id", nullable = false)
    private Long chargePriceId;

    @Column(name = "proposed_value", nullable = false, precision = 12, scale = 2)
    private BigDecimal proposedValue;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.PENDING;

    @Column(name = "proposed_by", nullable = false, length = 100)
    private String proposedBy;

    @Column(name = "proposed_at", nullable = false)
    private LocalDateTime proposedAt = LocalDateTime.now();

    @Column(length = 500)
    private String reason;

    @Column(name = "decided_by", length = 100)
    private String decidedBy;

    @Column(name = "decided_at")
    private LocalDateTime decidedAt;

    @Column(name = "decision_reason", length = 500)
    private String decisionReason;
}
