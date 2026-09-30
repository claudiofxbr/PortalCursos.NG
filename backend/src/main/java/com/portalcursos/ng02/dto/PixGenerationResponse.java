package com.portalcursos.ng02.dto;

import com.fasterxml.jackson.annotation.JsonUnwrapped;
import com.portalcursos.ng02.model.Payment;

/**
 * Resposta de {@code POST /api/finance/generate-pix/{paymentId}}. {@link Payment} é
 * serializado "achatado" na raiz do JSON via {@code @JsonUnwrapped} para preservar o
 * contrato legado que o frontend já consome hoje (lia o {@code Payment} inteiro
 * diretamente na raiz — ver {@code frontend/app/finance/page.tsx}), só adicionando
 * {@code qrCodeImageUrl} como campo extra.
 */
public record PixGenerationResponse(@JsonUnwrapped Payment payment, String qrCodeImageUrl) {
}
