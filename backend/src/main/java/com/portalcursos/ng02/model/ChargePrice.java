package com.portalcursos.ng02.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Valor de uma cobrança (seção 11 do ExportaçãoPortalCursos.NG.md). {@code courseId} nulo = valor global;
 * {@code currentValue} nulo = ainda não preenchido. Alterar o valor aqui NÃO muda faturas já emitidas.
 */
@Entity
@Table(name = "charge_prices")
@Getter
@Setter
@NoArgsConstructor
public class ChargePrice {

    public enum Scope { ALL, GRADUATION, POSTGRAD }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 60)
    private String code;

    @Column(nullable = false, length = 80)
    private String category;

    @Column(nullable = false, length = 200)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "academic_scope", nullable = false, length = 20)
    private Scope academicScope = Scope.ALL;

    @Column(name = "course_id")
    private UUID courseId;

    @Column(name = "current_value", precision = 12, scale = 2)
    private BigDecimal currentValue;

    @Column(nullable = false)
    private boolean active = true;

    @Version
    private Long version;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "updated_by", length = 100)
    private String updatedBy;
}
