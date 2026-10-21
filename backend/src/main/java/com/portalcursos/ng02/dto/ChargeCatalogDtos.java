package com.portalcursos.ng02.dto;

import com.portalcursos.ng02.model.EPaymentCategory;
import com.portalcursos.ng02.model.ESecretaryProcessType;

import java.math.BigDecimal;

/** Contrato da seleção de procedimento ao gerar uma cobrança para o aluno. */
public final class ChargeCatalogDtos {

    private ChargeCatalogDtos() {
    }

    /** De onde o valor foi resolvido: linha do curso do aluno ou linha global. */
    public enum ValueSource { COURSE, GLOBAL }

    /**
     * Um procedimento cobrável para o aluno, já com o valor que será cobrado. {@code valueDefined=false}
     * significa "valor ainda não definido na tabela": o procedimento aparece, mas não pode ser lançado.
     */
    public record CatalogItemDto(Long priceId, String code, String name, String group,
                                 EPaymentCategory paymentCategory, ESecretaryProcessType processType,
                                 BigDecimal value, ValueSource valueSource, boolean valueDefined,
                                 String defaultDescription) {
    }
}
