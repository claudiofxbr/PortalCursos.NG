package com.portalcursos.ng02.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Pedido de valor diferente do da tabela de valores. Nada é cobrado enquanto estiver {@code PENDING}: só a
 * aprovação de um administrador (que não seja quem pediu) emite a cobrança, com o valor autorizado.
 */
@Entity
@Table(name = "charge_amount_exceptions")
@Getter
@Setter
@NoArgsConstructor
public class ChargeAmountException {

    public enum Status { PENDING, APPROVED, REJECTED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "student_id", nullable = false)
    private Long studentId;

    @Enumerated(EnumType.STRING)
    @Column(name = "academic_level", nullable = false, length = 20)
    private EAcademicLevel academicLevel;

    @Column(name = "charge_price_id", nullable = false)
    private Long chargePriceId;

    /** Valor da tabela no momento do pedido (para o administrador comparar). */
    @Column(name = "table_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal tableAmount;

    @Column(name = "requested_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal requestedAmount;

    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;

    @Column(length = 500)
    private String description;

    @Column(nullable = false, length = 500)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.PENDING;

    @Column(name = "requested_by", nullable = false, length = 100)
    private String requestedBy;

    @Column(name = "requested_at", nullable = false)
    private LocalDateTime requestedAt = LocalDateTime.now();

    @Column(name = "decided_by", length = 100)
    private String decidedBy;

    @Column(name = "decided_at")
    private LocalDateTime decidedAt;

    @Column(name = "decision_reason", length = 500)
    private String decisionReason;

    @Column(name = "payment_id")
    private Long paymentId;
}
