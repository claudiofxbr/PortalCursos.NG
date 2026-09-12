package com.portalcursos.ng02.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
import java.time.LocalDate;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonIgnore;

import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

@Entity
@Table(name = "payments")
@Data
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@EqualsAndHashCode(callSuper=true)
@SQLDelete(sql = "UPDATE payments SET active = false WHERE id = ?")
@SQLRestriction("active = true")
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

    /**
     * Referencia tanto alunos de graduação quanto de pós — PostgradStudent é uma
     * subclasse de Student (single-table inheritance), então uma única FK basta;
     * {@link #academicLevel} indica qual dos dois é.
     */
    // Student mantém @SQLRestriction (soft-delete real). @NotFound(IGNORE) faz
    // getStudent() devolver null em vez de lançar EntityNotFoundException quando o
    // aluno foi desativado — FinancialController.ownsStudentRecord já checa != null.
    // A associação é LAZY; é seguro porque as queries de listagem do
    // PaymentRepository fazem LEFT JOIN FETCH p.student explicitamente para
    // não gerar N+1.
    @ManyToOne(fetch = FetchType.LAZY)
    @org.hibernate.annotations.NotFound(action = org.hibernate.annotations.NotFoundAction.IGNORE)
    @JoinColumn(name = "student_id", referencedColumnName = "id")
    @JsonIgnore
    private Student student;

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

