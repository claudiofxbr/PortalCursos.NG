package com.portalcursos.ng02.dto;

/**
 * Resultado da criação de um pedido PIX no PagBank, já extraído da resposta bruta do PSP.
 *
 * @param pspOrderId    id do pedido no PagBank (ex: "ORDE_XXXX")
 * @param copiaECola    texto do BR Code (copia-e-cola) do QR Code PIX
 * @param qrCodeImageUrl link para a imagem PNG do QR Code (transitório, não persistido)
 */
public record PixOrderResult(String pspOrderId, String copiaECola, String qrCodeImageUrl) {
}
