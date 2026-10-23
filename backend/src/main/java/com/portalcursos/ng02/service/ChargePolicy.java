package com.portalcursos.ng02.service;

import com.portalcursos.ng02.repository.ChargePriceRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Regra "o valor da cobrança vem sempre da tabela de valores" (exceção só por pedido da secretária do curso,
 * aprovado por administrador).
 *
 * <p>A regra vem <b>ligada</b> por configuração, mas só <b>vale de fato</b> quando a tabela está pronta: existe
 * ao menos uma linha global e nenhuma linha global está em branco. Com a tabela vazia ou incompleta, ligar a
 * regra impediria a emissão de qualquer cobrança; por isso ela fica em espera e passa a valer sozinha assim que
 * o administrador preenche o último valor global. Valores já preenchidos não podem ser apagados (a tabela
 * recusa valor nulo), então a prontidão não "desliga" depois de alcançada.
 */
@Component
public class ChargePolicy {

    private final boolean configured;
    private final ChargePriceRepository priceRepository;

    public ChargePolicy(@Value("${portalcursos.charges.enforce-table-price:true}") boolean configured,
                        ChargePriceRepository priceRepository) {
        this.configured = configured;
        this.priceRepository = priceRepository;
    }

    /** A regra está habilitada na configuração (independe de a tabela estar pronta). */
    public boolean configured() {
        return configured;
    }

    /** Quantas linhas globais ainda estão sem valor. */
    public long blankGlobalValues() {
        return priceRepository.countByActiveTrueAndCourseIdIsNullAndCurrentValueIsNull();
    }

    /** Há linhas globais e nenhuma está em branco. */
    public boolean tableReady() {
        return priceRepository.countByActiveTrueAndCourseIdIsNull() > 0 && blankGlobalValues() == 0;
    }

    /** A regra está valendo agora: habilitada e com a tabela pronta. É o que as validações devem consultar. */
    public boolean enforceTablePrice() {
        return configured && tableReady();
    }
}
