package com.portalcursos.ng02.controller;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.portalcursos.ng02.model.User;
import com.portalcursos.ng02.model.UserSession;
import com.portalcursos.ng02.repository.UserRepository;
import com.portalcursos.ng02.repository.UserSessionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.UUID;

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

    @Autowired
    private UserSessionRepository userSessionRepository;

    @Autowired
    private UserRepository userRepository;

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

    // Cobertura do Lote E, item A3: signup extraído de AuthController.registerUser para
    // AuthService.signup (checagem de role privilegiada, unicidade de username/e-mail).

    @Test
    public void testSignupComRolePrivilegiadaSemAutenticacaoRetorna403() throws Exception {
        String payload = "{"
                + "\"username\":\"tentativaadmin\","
                + "\"email\":\"tentativaadmin@example.com\","
                + "\"password\":\"senha123\","
                + "\"role\":[\"admin\"],"
                + "\"privacyConsentAccepted\":true"
                + "}";

        mockMvc.perform(post("/api/auth/signup")
                .header("X-Real-IP", "10.10.30.1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Apenas administradores podem registrar contas privilegiadas."))
                .andExpect(jsonPath("$.timestamp").doesNotExist());

        assertFalse(userRepository.existsByUsername("tentativaadmin"),
                "Usuário não deve ser criado quando a role privilegiada é negada");
    }

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    public void testSignupComRolePrivilegiadaAutenticadoComoAdminFunciona() throws Exception {
        String payload = "{"
                + "\"username\":\"novacoordenadora\","
                + "\"email\":\"novacoordenadora@example.com\","
                + "\"password\":\"senha123\","
                + "\"role\":[\"coordenador\"],"
                + "\"privacyConsentAccepted\":true"
                + "}";

        mockMvc.perform(post("/api/auth/signup")
                .header("X-Real-IP", "10.10.30.2")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Usuário registrado com sucesso."));

        assertTrue(userRepository.existsByUsername("novacoordenadora"));
    }

    @Test
    public void testSignupComUsernameJaExistenteRetorna400() throws Exception {
        String payload = "{"
                + "\"username\":\"admin\","
                + "\"email\":\"outroemail@example.com\","
                + "\"password\":\"senha123\","
                + "\"role\":[\"aluno\"],"
                + "\"privacyConsentAccepted\":true"
                + "}";

        mockMvc.perform(post("/api/auth/signup")
                .header("X-Real-IP", "10.10.30.3")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Erro: Nome de usuário já está em uso."))
                .andExpect(jsonPath("$.timestamp").doesNotExist());
    }

    @Test
    public void testSignupComEmailJaExistenteRetorna400() throws Exception {
        String payload = "{"
                + "\"username\":\"usuarionovo123\","
                + "\"email\":\"admin@portalcursos.com\","
                + "\"password\":\"senha123\","
                + "\"role\":[\"aluno\"],"
                + "\"privacyConsentAccepted\":true"
                + "}";

        mockMvc.perform(post("/api/auth/signup")
                .header("X-Real-IP", "10.10.30.4")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Erro: E-mail já está em uso."))
                .andExpect(jsonPath("$.timestamp").doesNotExist());
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

    // Cobertura do Lote E, item A2: refreshtoken extraído de AuthController.refreshtoken para
    // AuthService.refreshToken (lookup de sessão, checagem de expiração, rotação de token).

    @Test
    public void testRefreshTokenValidoRenovaSessaoERotacionaToken() throws Exception {
        User admin = userRepository.findByUsername("admin").orElseThrow();
        String oldToken = UUID.randomUUID().toString();
        userSessionRepository.save(UserSession.builder()
                .user(admin)
                .refreshToken(oldToken)
                .expiryDate(Instant.now().plusSeconds(3600))
                .build());

        mockMvc.perform(post("/api/auth/refreshtoken")
                .header("X-Real-IP", "10.10.20.1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"refreshToken\":\"" + oldToken + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Token renovado com sucesso."))
                .andExpect(cookie().exists("portal_access_token"))
                .andExpect(cookie().exists("portal_refresh_token"));

        assertFalse(userSessionRepository.findByRefreshToken(oldToken).isPresent(),
                "Refresh token antigo deve ser invalidado (rotação)");
    }

    @Test
    public void testRefreshTokenExpiradoRetorna403EInvalidaSessao() throws Exception {
        User admin = userRepository.findByUsername("admin").orElseThrow();
        String expiredToken = UUID.randomUUID().toString();
        userSessionRepository.save(UserSession.builder()
                .user(admin)
                .refreshToken(expiredToken)
                .expiryDate(Instant.now().minusSeconds(60))
                .build());

        mockMvc.perform(post("/api/auth/refreshtoken")
                .header("X-Real-IP", "10.10.20.2")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"refreshToken\":\"" + expiredToken + "\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Sessão expirada. Faça login novamente."));

        assertFalse(userSessionRepository.findByRefreshToken(expiredToken).isPresent(),
                "Sessão expirada deve ser removida do banco");
    }

    @Test
    public void testRefreshTokenInexistenteRetorna403() throws Exception {
        mockMvc.perform(post("/api/auth/refreshtoken")
                .header("X-Real-IP", "10.10.20.3")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"refreshToken\":\"" + UUID.randomUUID() + "\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Sessão inválida. Faça login novamente."));
    }

    @Test
    public void testRefreshTokenSemTokenRetorna400() throws Exception {
        mockMvc.perform(post("/api/auth/refreshtoken")
                .header("X-Real-IP", "10.10.20.4")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Refresh token não fornecido."));
    }
}
