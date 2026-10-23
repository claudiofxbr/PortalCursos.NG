package com.portalcursos.ng02.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.portalcursos.ng02.service.PixConfigCheck.Finding;
import com.portalcursos.ng02.service.PixConfigCheck.Report;
import org.junit.jupiter.api.Test;

import java.util.List;

/** Cada regra da conferência de configuração do Pix, e a garantia de que nenhum valor secreto sai no resultado. */
class PixConfigCheckTest {

    private static final String TOKEN = "a1b2c3d4e5f60718293a4b5c6d7e8f90";
    private static final String SECRET = "segredo-do-webhook-com-mais-de-32-caracteres-xyz";
    private static final String SANDBOX = "https://sandbox.api.pagseguro.com";
    private static final String PROD = "https://api.pagseguro.com";
    private static final String PUBLIC_API = "https://xavierbr-vps.tech";
    private static final String PUBLIC_APP = "https://xavierbr-vps.tech/portalcursos.ng";

    private static PixConfigCheck check(String token, String baseUrl, String secret, String publicApi, String publicApp, String env) {
        return new PixConfigCheck(token, baseUrl, secret, publicApi, publicApp, env);
    }

    private static List<String> variables(Report report) {
        return report.findings().stream().map(Finding::variable).toList();
    }

    @Test
    void producaoCompletaECoerenteEstaOk() {
        Report r = check(TOKEN, PROD, SECRET, PUBLIC_API, PUBLIC_APP, "production").check();

        assertEquals(PixConfigCheck.OK, r.status());
        assertEquals("PRODUCAO", r.environment());
        assertTrue(r.findings().isEmpty());
    }

    @Test
    void sandboxCompletoEstaOkEAmbienteESandbox() {
        Report r = check(TOKEN, SANDBOX, SECRET, PUBLIC_API, PUBLIC_APP, "sandbox").check();

        assertEquals(PixConfigCheck.OK, r.status());
        assertEquals("SANDBOX", r.environment());
    }

    @Test
    void semTokenOuSemSegredoNaoEstaConfigurado() {
        Report semToken = check("", PROD, SECRET, PUBLIC_API, PUBLIC_APP, "production").check();
        Report semSegredo = check(TOKEN, PROD, "  ", PUBLIC_API, PUBLIC_APP, "production").check();

        assertEquals(PixConfigCheck.NAO_CONFIGURADO, semToken.status());
        assertTrue(variables(semToken).contains("PAGSEGURO_API_TOKEN"));
        assertEquals(PixConfigCheck.NAO_CONFIGURADO, semSegredo.status());
        assertTrue(variables(semSegredo).contains("PAGSEGURO_WEBHOOK_SECRET"));
    }

    @Test
    void padraoDoComposeEmProducaoSemSegredosPedeTokenSegredoEAmbiente() {
        // O que o docker-compose.prod.yml entrega sem .env: token e segredo vazios, API de produção, ambiente sandbox.
        Report r = check("", PROD, "", PUBLIC_API, PUBLIC_APP, "sandbox").check();

        assertEquals(PixConfigCheck.NAO_CONFIGURADO, r.status());
        assertTrue(variables(r).containsAll(List.of("PAGSEGURO_API_TOKEN", "PAGSEGURO_WEBHOOK_SECRET", "PAGBANK_ENV")));
    }

    @Test
    void tokenDeExemploOuCurtoEhApontadoSemSerNaoConfigurado() {
        Report exemplo = check("SEU_TOKEN_AQUI", PROD, SECRET, PUBLIC_API, PUBLIC_APP, "production").check();
        Report curto = check("abc123", PROD, SECRET, PUBLIC_API, PUBLIC_APP, "production").check();

        assertEquals(PixConfigCheck.ATENCAO, exemplo.status());
        assertTrue(variables(exemplo).contains("PAGSEGURO_API_TOKEN"));
        assertTrue(variables(curto).contains("PAGSEGURO_API_TOKEN"));
    }

    @Test
    void segredoDoWebhookCurtoEhApontado() {
        Report r = check(TOKEN, PROD, "curto-demais", PUBLIC_API, PUBLIC_APP, "production").check();

        assertEquals(PixConfigCheck.ATENCAO, r.status());
        assertTrue(variables(r).contains("PAGSEGURO_WEBHOOK_SECRET"));
    }

    @Test
    void ambienteProductionComUrlDeSandboxEhIncoerente() {
        Report r = check(TOKEN, SANDBOX, SECRET, PUBLIC_API, PUBLIC_APP, "production").check();

        assertEquals(PixConfigCheck.ATENCAO, r.status());
        assertTrue(variables(r).contains("PAGBANK_ENV"));
    }

    @Test
    void apiDeProducaoSemAmbienteProductionAvisaDoCartaoRestrito() {
        Report r = check(TOKEN, PROD, SECRET, PUBLIC_API, PUBLIC_APP, "sandbox").check();

        assertEquals(PixConfigCheck.ATENCAO, r.status());
        assertTrue(r.findings().stream().anyMatch(f -> f.variable().equals("PAGBANK_ENV") && f.problem().contains("cartão")));
    }

    @Test
    void urlDaApiDesconhecidaEhApontada() {
        Report r = check(TOKEN, "https://exemplo.com", SECRET, PUBLIC_API, PUBLIC_APP, "sandbox").check();

        assertEquals("DESCONHECIDO", r.environment());
        assertTrue(variables(r).contains("PAGSEGURO_API_BASE_URL"));
    }

    @Test
    void urlPublicaDaApiPrecisaSerHttpsSemLocalhost() {
        for (String bad : List.of("http://xavierbr-vps.tech", "https://localhost:8080", "https://127.0.0.1", "", "xavierbr-vps.tech")) {
            Report r = check(TOKEN, PROD, SECRET, bad, PUBLIC_APP, "production").check();

            assertTrue(variables(r).contains("PORTALCURSOS_PUBLIC_API_URL"), "deveria recusar: '" + bad + "'");
        }
    }

    @Test
    void urlPublicaDoAppHttpEhApontadaMasVaziaNao() {
        assertTrue(variables(check(TOKEN, PROD, SECRET, PUBLIC_API, "http://xavierbr-vps.tech/x", "production").check())
                .contains("PORTALCURSOS_PUBLIC_APP_URL"));
        assertFalse(variables(check(TOKEN, PROD, SECRET, PUBLIC_API, "", "production").check())
                .contains("PORTALCURSOS_PUBLIC_APP_URL"));
    }

    @Test
    void oResultadoNuncaTrazValorDeTokenNemDeSegredo() {
        Report r = check(TOKEN, SANDBOX, "curto", "http://localhost:8080", PUBLIC_APP, "production").check();

        String everything = r.toString();
        assertFalse(everything.contains(TOKEN), "o token não pode aparecer");
        assertFalse(everything.contains("curto-demais") || everything.contains(SECRET), "o segredo não pode aparecer");
    }
}
