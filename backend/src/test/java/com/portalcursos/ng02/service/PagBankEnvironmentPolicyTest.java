package com.portalcursos.ng02.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PagBankEnvironmentPolicyTest {

    @Test
    void producaoSoComValorExato() {
        assertTrue(new PagBankEnvironmentPolicy("production", "").isProduction());
        assertFalse(new PagBankEnvironmentPolicy("Production", "").isProduction());
        assertFalse(new PagBankEnvironmentPolicy("prod", "").isProduction());
        assertFalse(new PagBankEnvironmentPolicy("", "").isProduction());
        assertFalse(new PagBankEnvironmentPolicy("sandbox", "").isProduction());
    }

    @Test
    void emProducaoQualquerUsuarioPaga() {
        assertTrue(new PagBankEnvironmentPolicy("production", "").canPay(false, "aluno@x.com"));
    }

    @Test
    void foraDeProducaoSoAdminOuTestador() {
        PagBankEnvironmentPolicy p = new PagBankEnvironmentPolicy("sandbox", " Teste@X.com , outro@x.com ,, ");
        assertTrue(p.canPay(true, null));
        assertTrue(p.canPay(false, "teste@x.com"));
        assertTrue(p.canPay(false, "OUTRO@x.com"));
        assertFalse(p.canPay(false, "aluno@x.com"));
        assertFalse(p.canPay(false, null));
        assertFalse(p.canPay(false, ""));
    }

    @Test
    void semListaDeTestadoresNinguemNaoAdminPaga() {
        assertFalse(new PagBankEnvironmentPolicy("sandbox", "").canPay(false, "aluno@x.com"));
    }
}
