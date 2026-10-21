package com.portalcursos.ng02.dto;

import com.portalcursos.ng02.model.ChargeAmountException;
import com.portalcursos.ng02.model.EAcademicLevel;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** Contratos do pedido de exceção ao valor da tabela e da política de valores. */
public final class ChargeExceptionDtos {

    private ChargeExceptionDtos() {
    }

    public record ExceptionRequest(Long studentId, EAcademicLevel academicLevel, Long chargePriceId,
                                   LocalDate dueDate, String description, BigDecimal requestedAmount, String reason) {
    }

    public record DecisionRequest(String reason) {
    }

    public record ExceptionDto(Long id, Long studentId, String studentName, EAcademicLevel academicLevel,
                               Long chargePriceId, String procedureName, BigDecimal tableAmount,
                               BigDecimal requestedAmount, LocalDate dueDate, String description, String reason,
                               ChargeAmountException.Status status, String requestedBy, LocalDateTime requestedAt,
                               String decidedBy, LocalDateTime decidedAt, String decisionReason, Long paymentId) {
    }

    /**
     * O que a tela precisa saber: se a regra "valor sempre da tabela" já está ligada e o que o usuário pode
     * fazer (pedir uma exceção, ou decidir os pedidos).
     */
    public record PolicyDto(boolean enforceTablePrice, boolean canRequestException, boolean canDecideException) {
    }
}
