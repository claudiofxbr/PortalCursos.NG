package com.portalcursos.ng02.dto;

import com.portalcursos.ng02.model.EAcademicLevel;
import com.portalcursos.ng02.model.EPaymentCategory;
import com.portalcursos.ng02.model.EPaymentMethod;
import com.portalcursos.ng02.model.EPaymentStatus;
import com.portalcursos.ng02.model.ESecretaryProcessType;
import com.portalcursos.ng02.model.Payment;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Fatura como o aluno pode ver: só o que ele precisa para entender e pagar. Fica de fora tudo que é do PSP
 * (ids de pedido/checkout, valor em centavos) e do controle interno (versão, quem alterou, linha da tabela de valores),
 * para que um campo novo na entidade {@link Payment} nunca chegue ao aluno sem alguém decidir isso.
 */
public record PaymentView(
        Long id,
        EPaymentStatus status,
        EPaymentMethod method,
        String paymentCode,
        BigDecimal amount,
        BigDecimal totalAmount,
        BigDecimal interestAmount,
        LocalDate dueDate,
        EAcademicLevel academicLevel,
        EPaymentCategory category,
        ESecretaryProcessType secretaryProcessType,
        String description,
        LocalDateTime createdAt) {

    public static PaymentView from(Payment p) {
        return new PaymentView(p.getId(), p.getStatus(), p.getMethod(), p.getPaymentCode(), p.getAmount(),
                p.getTotalAmount(), p.getInterestAmount(), p.getDueDate(), p.getAcademicLevel(), p.getCategory(),
                p.getSecretaryProcessType(), p.getDescription(), p.getCreatedAt());
    }
}
