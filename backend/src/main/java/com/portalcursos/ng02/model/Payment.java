package com.portalcursos.ng02.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
import java.time.LocalDate;
import com.fasterxml.jackson.annotation.JsonProperty;

import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.Where;

@Entity
@Table(name = "payments")
@Data
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@SQLDelete(sql = "UPDATE payments SET active = false WHERE id = ?")
@Where(clause = "active = true")
public class Payment extends BaseAuditEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    private BigDecimal amount;

    @NotNull
    private LocalDate dueDate;

    @Enumerated(EnumType.STRING)
    private EPaymentStatus status;

    @Enumerated(EnumType.STRING)
    private EPaymentMethod method;

    private String paymentCode; // URL do Boleto ou QR Code do Pix

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id", referencedColumnName = "id")
    private Student student;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "postgrad_student_id", referencedColumnName = "id")
    private PostgradStudent postgradStudent;

    @Enumerated(EnumType.STRING)
    private EAcademicLevel academicLevel;

    @Enumerated(EnumType.STRING)
    private EPaymentCategory category;

    @Enumerated(EnumType.STRING)
    private ESecretaryProcessType secretaryProcessType;

    private String description;

    @Column(columnDefinition = "TEXT")
    private String studentPhotoUrl;

    /**
     * Calcula o valor dos juros (1.8% ao mês pro-rata die)
     */
    @JsonProperty("interestAmount")
    public BigDecimal getInterestAmount() {
        if (status != EPaymentStatus.OVERDUE || dueDate == null || amount == null) {
            return BigDecimal.ZERO;
        }
        
        LocalDate now = LocalDate.now();
        if (now.isBefore(dueDate)) {
            return BigDecimal.ZERO;
        }

        long daysOverdue = java.time.temporal.ChronoUnit.DAYS.between(dueDate, now);
        // 1.8% ao mês = 0.06% ao dia (assumindo mês de 30 dias)
        BigDecimal dailyInterestRate = new BigDecimal("0.0006"); 
        return amount.multiply(dailyInterestRate).multiply(new BigDecimal(daysOverdue))
                .setScale(2, java.math.RoundingMode.HALF_UP);
    }

    /**
     * Retorna o valor total (Principal + Juros)
     */
    @JsonProperty("totalAmount")
    public BigDecimal getTotalAmount() {
        if (amount == null) return BigDecimal.ZERO;
        return amount.add(getInterestAmount());
    }
}

