package com.portalcursos.ng02.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
    "SPRING_DATASOURCE_URL=jdbc:h2:mem:authtestdb;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
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
public class AuthControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    // Regressão: catch de IllegalArgumentException no signup deve delegar ao
    // GlobalExceptionHandler via BusinessException (400 com corpo padronizado),
    // não mais montar ResponseEntity manual com "Erro: " + mensagem.
    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    public void testSignupWithInvalidRoleReturnsStandardizedErrorBody() throws Exception {
        String payload = "{"
                + "\"username\":\"usuarioroleinvalida\","
                + "\"email\":\"usuarioroleinvalida@example.com\","
                + "\"password\":\"senha123\","
                + "\"role\":[\"role_que_nao_existe\"],"
                + "\"privacyConsentAccepted\":true"
                + "}";

        mockMvc.perform(post("/api/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value("Role desconhecida: role_que_nao_existe"))
                .andExpect(jsonPath("$.path").exists())
                // Formato antigo tinha somente "message" (sem os demais campos do handler padrão)
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    // Cobertura do Lote E, item A1: login extraído de AuthController.authenticateUser para
    // AuthService.login. IPs distintos por teste (X-Real-IP) isolam o contador de
    // LoginAttemptService, que é por IP e persiste no mesmo contexto Spring entre os testes
    // desta classe.

    @Test
    public void testSigninComCredenciaisValidasRetornaCookiesEDadosDoUsuario() throws Exception {
        String payload = "{\"username\":\"admin\",\"password\":\"TestAdminPass123!\"}";

        mockMvc.perform(post("/api/auth/signin")
                .header("X-Real-IP", "10.10.10.1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("admin"))
                .andExpect(jsonPath("$.token").doesNotExist())
                .andExpect(cookie().exists("portal_access_token"))
                .andExpect(cookie().exists("portal_refresh_token"));
    }

    @Test
    public void testSigninComSenhaErradaRetorna401() throws Exception {
        String payload = "{\"username\":\"admin\",\"password\":\"senha-errada-123\"}";

        mockMvc.perform(post("/api/auth/signin")
                .header("X-Real-IP", "10.10.10.2")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Erro de Autenticação: Usuário ou senha inválidos."));
    }

    @Test
    public void testSigninBloqueiaIpAposCincoFalhas() throws Exception {
        String payloadErrado = "{\"username\":\"admin\",\"password\":\"senha-errada-123\"}";
        String ip = "10.10.10.3";

        for (int i = 0; i < 5; i++) {
            mockMvc.perform(post("/api/auth/signin")
                    .header("X-Real-IP", ip)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(payloadErrado))
                    .andExpect(status().isUnauthorized());
        }

        // 6ª tentativa (mesmo com senha certa): IP já bloqueado por força bruta
        String payloadCerto = "{\"username\":\"admin\",\"password\":\"TestAdminPass123!\"}";
        mockMvc.perform(post("/api/auth/signin")
                .header("X-Real-IP", ip)
                .contentType(MediaType.APPLICATION_JSON)
                .content(payloadCerto))
                .andExpect(status().isLocked())
                .andExpect(jsonPath("$.message").value(
                        "Acesso temporariamente bloqueado por excesso de tentativas. Tente novamente em 15 minutos."));
    }
}
