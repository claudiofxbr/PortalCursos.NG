package com.portalcursos.ng02.dto;

/**
 * Resultado da criação do checkout hospedado do PagBank (cartão).
 *
 * @param checkoutId         id do checkout no PagBank (ex: "CHEC_XXXX")
 * @param payUrl             link https da página de pagamento hospedada
 * @param chargedAmountCents valor em centavos enviado ao PagBank; é o que a baixa deve comparar
 */
public record CardCheckoutResult(String checkoutId, String payUrl, long chargedAmountCents) {
}
