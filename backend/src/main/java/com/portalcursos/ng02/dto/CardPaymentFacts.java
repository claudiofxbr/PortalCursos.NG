package com.portalcursos.ng02.dto;

/**
 * Fatos de pagamento de um checkout de cartão, lidos da API do PagBank (nunca do corpo da notificação).
 *
 * @param referenceId     reference_id do checkout devolvido pela API
 * @param paid            true se existe ao menos uma cobrança PAID nos pedidos do checkout
 * @param paidAmountCents soma em centavos das cobranças PAID, ou null se algum valor veio ilegível
 * @param methodType      tipo do meio de pagamento da cobrança paga ("CREDIT_CARD"/"DEBIT_CARD"), ou null
 */
public record CardPaymentFacts(String referenceId, boolean paid, Long paidAmountCents, String methodType) {
}
