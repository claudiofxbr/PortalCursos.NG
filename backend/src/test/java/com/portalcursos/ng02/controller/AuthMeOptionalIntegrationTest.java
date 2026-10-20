package com.portalcursos.ng02.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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

import java.util.List;

/**
 * E-04 (relatório de testes de 20/10/2026): o frontend consulta /api/auth/me a cada carga de página, antes do
 * login, e o 401 enchia o console de erros. Com ?optional=true "sem sessão" vira 200 {authenticated:false}.
 * Sem o parâmetro o contrato antigo (401) continua igual.
 */
@SpringBootTest(properties = {
    "SPRING_DATASOURCE_URL=jdbc:h2:mem:authmeoptionaltestdb;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
    "SPRING_DATASOURCE_USERNAME=sa",
    "SPRING_DATASOURCE_PASSWORD=",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.flyway.enabled=false",
    "APP_JWT_SECRET=ZXhhbXBsZS1zZWNyZXQta2V5LXdpdGgtZW5vdWdoLWxlbmd0aC1mb3ItYmFzZTY0LWVuY29kaW5nLXByb3Blcmx5",
    "APP_JWT_EXPIRATION=900000",
    "APP_ROOT_PASSWORD=TestRootPass123!",
    "APP_ADMIN_PASSWORD=TestAdminPass123!"
})
@AutoConfigureMockMvc
class AuthMeOptionalIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    private static org.springframework.test.web.servlet.request.RequestPostProcessor loggedIn() {
        return user(new UserDetailsImpl(77L, "maria.teste", "maria@example.com", "x",
                List.of(new SimpleGrantedAuthority("ROLE_SECRETARIA"))));
    }

    @Test
    void semSessaoESemParametroContinuaRetornando401() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Não autenticado."));
    }

    @Test
    void semSessaoComOptionalFalseTambemRetorna401() throws Exception {
        mockMvc.perform(get("/api/auth/me?optional=false")).andExpect(status().isUnauthorized());
    }

    @Test
    void semSessaoComOptionalTrueRetorna200EAuthenticatedFalse() throws Exception {
        mockMvc.perform(get("/api/auth/me?optional=true"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.authenticated").value(false))
                .andExpect(jsonPath("$.username").doesNotExist());
    }

    @Test
    void optionalComValorInvalidoNaoVira500() throws Exception {
        // optional=abc não converte para boolean: erro do cliente (400), nunca 500 (E-01).
        mockMvc.perform(get("/api/auth/me?optional=abc")).andExpect(status().isBadRequest());
    }

    @Test
    void comSessaoDevolveOUsuarioComEOuSemOptional() throws Exception {
        mockMvc.perform(get("/api/auth/me").with(loggedIn()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("maria.teste"))
                .andExpect(jsonPath("$.authenticated").doesNotExist());
        mockMvc.perform(get("/api/auth/me?optional=true").with(loggedIn()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("maria.teste"))
                .andExpect(jsonPath("$.email").value("maria@example.com"))
                .andExpect(jsonPath("$.authenticated").doesNotExist());
    }

    @Test
    void tokenForjadoComOptionalContinuaSendoTratadoComoSemSessao() throws Exception {
        mockMvc.perform(get("/api/auth/me?optional=true").header("Authorization", "Bearer token.falso.xyz"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authenticated").value(false));
    }
}
