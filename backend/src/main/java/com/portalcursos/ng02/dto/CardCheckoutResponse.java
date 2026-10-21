package com.portalcursos.ng02.dto;

/**
 * Resposta da criação do checkout de cartão. Nunca indica "aprovado": a baixa da fatura só ocorre
 * pelo webhook, depois de reconsultar o PagBank.
 *
 * @param url       página de pagamento hospedada do PagBank
 * @param paymentId fatura à qual o checkout pertence
 * @param reused    true quando um checkout recente e ainda válido da mesma fatura foi reaproveitado
 */
public record CardCheckoutResponse(String url, Long paymentId, boolean reused) {
}
