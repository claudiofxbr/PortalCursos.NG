package com.portalcursos.ng02.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Chave da regra "o valor da cobrança vem sempre da tabela de valores". Fica desligada até a tabela estar
 * preenchida (com ela ligada e a tabela em branco, nenhuma cobrança poderia ser emitida); ligá-la é uma
 * mudança de padrão no código, sem mexer na configuração do servidor.
 */
@Component
public class ChargePolicy {

    private final boolean enforceTablePrice;

    public ChargePolicy(@Value("${portalcursos.charges.enforce-table-price:false}") boolean enforceTablePrice) {
        this.enforceTablePrice = enforceTablePrice;
    }

    public boolean enforceTablePrice() {
        return enforceTablePrice;
    }
}
