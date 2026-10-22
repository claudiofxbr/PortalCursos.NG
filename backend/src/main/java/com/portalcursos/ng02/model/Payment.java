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

import org.hibernate.annotations.SQLRestriction;

// @SQLDelete removido (achado da auditoria): o SQL customizado não incluía o parâmetro
// de @Version que o Hibernate injeta para entidades versionadas, e paymentRepository.delete()
// lançava DataIntegrityViolationException sempre que chamado de verdade (confirmado via
// Testcontainers). Soft-delete real é feito explicitamente via setActive(false) + save()
// em PaymentService.deleteCharge, mesmo padrão já usado em UserService.deleteUser.
@Entity
@Table(name = "payments")
@Data
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@EqualsAndHashCode(callSuper=true)
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

    // Sem @Column explícito: a convenção padrão do Hibernate (CamelCase ->
    // snake_case) já resolve para "psp_order_id" (mesmo padrão usado por
    // studentPhotoUrl); confirmado contra a migração V23.
    private String pspOrderId; // id do pedido/cobrança PIX no gateway (PSP), para reconciliação via webhook

    // Pagamento por cartão (checkout hospedado): id do checkout (CHEC_...) e valor em centavos
    // fixado na criação do checkout, que é o que a baixa compara com o valor efetivamente pago.
    private String pspCheckoutId;

    private Long chargedAmountCents;

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

    /** Linha da tabela de valores de onde o valor saiu (nulo em cobranças anteriores ou de valor livre). */
    @Column(name = "charge_price_id")
    private Long chargePriceId;

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
     * Cobrança de valor zero (procedimento gratuito) não tem o que pagar: já nasce quitada, sem Pix nem
     * pendência. Só atua sobre fatura pendente com valor definido igual a zero.
     *
     * @return {@code true} se a fatura foi quitada agora
     */
    public boolean settleIfFree() {
        if (status == EPaymentStatus.PENDING && amount != null && amount.signum() == 0) {
            status = EPaymentStatus.PAID;
            return true;
        }
        return false;
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

