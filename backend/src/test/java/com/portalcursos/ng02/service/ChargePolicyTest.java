package com.portalcursos.ng02.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.lenient;

import com.portalcursos.ng02.repository.ChargePriceRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** A regra "valor sempre da tabela" só vale com a configuração ligada E a tabela pronta. */
@ExtendWith(MockitoExtension.class)
class ChargePolicyTest {

    @Mock private ChargePriceRepository repository;

    private ChargePolicy policy(boolean configured) {
        return new ChargePolicy(configured, repository);
    }

    private void table(long globalRows, long blank) {
        // lenient: o código curto-circuita (sem linhas globais nem consulta as em branco; desligada nem consulta a tabela)
        lenient().when(repository.countByActiveTrueAndCourseIdIsNull()).thenReturn(globalRows);
        lenient().when(repository.countByActiveTrueAndCourseIdIsNullAndCurrentValueIsNull()).thenReturn(blank);
    }

    @Test
    void tabelaCompletaComARegraLigadaVale() {
        table(24, 0);

        ChargePolicy p = policy(true);

        assertTrue(p.tableReady());
        assertTrue(p.enforceTablePrice());
        assertEquals(0, p.blankGlobalValues());
    }

    @Test
    void tabelaIncompletaDeixaARegraEmEsperaEInformaQuantosFaltam() {
        table(24, 24);

        ChargePolicy p = policy(true);

        assertFalse(p.tableReady());
        assertFalse(p.enforceTablePrice(), "com a tabela em branco ligar a regra impediria qualquer cobrança");
        assertEquals(24, p.blankGlobalValues());
    }

    @Test
    void faltandoSoUmValorAindaNaoVale() {
        table(24, 1);

        assertFalse(policy(true).enforceTablePrice());
    }

    @Test
    void tabelaSemNenhumaLinhaGlobalNaoContaComoPronta() {
        table(0, 0);

        ChargePolicy p = policy(true);

        assertFalse(p.tableReady());
        assertFalse(p.enforceTablePrice());
    }

    @Test
    void configuracaoDesligadaNaoValeMesmoComATabelaPronta() {
        table(24, 0);

        ChargePolicy p = policy(false);

        assertTrue(p.tableReady());
        assertFalse(p.configured());
        assertFalse(p.enforceTablePrice());
    }
}
