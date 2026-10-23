package com.portalcursos.ng02.controller;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.portalcursos.ng02.service.UserDetailsImpl;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.List;

/** Rota de estado do Pix (só administrador), resumo público no /api/health e ausência de vazamento de segredos. */
@SpringBootTest(properties = {
    "SPRING_DATASOURCE_URL=jdbc:h2:mem:pixconfigtestdb;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
    "SPRING_DATASOURCE_USERNAME=sa",
    "SPRING_DATASOURCE_PASSWORD=",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.flyway.enabled=false",
    "pagseguro.api.token=TOKEN-DE-TESTE-NAO-PODE-VAZAR-0123456789",
    "pagseguro.webhook.secret=SEGREDO-DE-TESTE-NAO-PODE-VAZAR-0123456789-ABCDEF",
    "pagseguro.api.base-url=https://sandbox.api.pagseguro.com",
    "portalcursos.public-api-url=http://localhost:8080",
    "APP_JWT_SECRET=ZXhhbXBsZS1zZWNyZXQta2V5LXdpdGgtZW5vdWdoLWxlbmd0aC1mb3ItYmFzZTY0LWVuY29kaW5nLXByb3Blcmx5",
    "APP_JWT_EXPIRATION=900000",
    "APP_ROOT_PASSWORD=TestRootPass123!",
    "APP_ADMIN_PASSWORD=TestAdminPass123!"
})
@AutoConfigureMockMvc
class PixConfigControllerIntegrationTest {

    @Autowired private MockMvc mockMvc;

    private static RequestPostProcessor as(String role) {
        return user(new UserDetailsImpl(1L, "u-" + role, "u@x.com", "x", List.of(new SimpleGrantedAuthority("ROLE_" + role))));
    }

    @Test
    void administradorVeOEstadoEAsVariaveisAAjustarSemNenhumValorSecreto() throws Exception {
        mockMvc.perform(get("/api/finance/pix-config").with(as("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", containsString("no-store")))
                .andExpect(jsonPath("$.status").value("ATENCAO"))
                .andExpect(jsonPath("$.environment").value("SANDBOX"))
                .andExpect(jsonPath("$.findings[?(@.variable=='PORTALCURSOS_PUBLIC_API_URL')]").isNotEmpty())
                .andExpect(content().string(not(containsString("TOKEN-DE-TESTE"))))
                .andExpect(content().string(not(containsString("SEGREDO-DE-TESTE"))));
    }

    @Test
    void rootMasterTambemVe() throws Exception {
        mockMvc.perform(get("/api/finance/pix-config").with(as("ROOT_MASTER"))).andExpect(status().isOk());
    }

    @Test
    void demaisPerfisRecebem403EsemLoginRecebe401() throws Exception {
        for (String role : List.of("SECRETARIA", "FINANCEIRO", "COORDENADOR", "ALUNO")) {
            mockMvc.perform(get("/api/finance/pix-config").with(as(role))).andExpect(status().isForbidden());
        }
        mockMvc.perform(get("/api/finance/pix-config")).andExpect(status().isUnauthorized());
    }

    @Test
    void healthPublicoTrazSoUmaPalavraSobreOPixSemAmbienteNemSegredos() throws Exception {
        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.diagnostics.pix").value("ATENCAO"))
                .andExpect(content().string(not(containsString("TOKEN-DE-TESTE"))))
                .andExpect(content().string(not(containsString("SEGREDO-DE-TESTE"))))
                .andExpect(content().string(not(containsString("SANDBOX"))))
                .andExpect(content().string(not(containsString("PORTALCURSOS_PUBLIC_API_URL"))));
    }
}
