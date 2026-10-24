package com.portalcursos.ng02.controller;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.portalcursos.ng02.repository.LoginAttemptRepository;
import com.portalcursos.ng02.repository.StaffMemberRepository;
import com.portalcursos.ng02.repository.StudentRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Não mocka UserRepository/RoleRepository: o DataLoader (CommandLineRunner) depende deles
 * para semear as roles do enum e os usuários "rootmaster"/"admin" na subida do contexto —
 * mockar quebraria a inicialização (mesmo padrão usado pelo AuthControllerIntegrationTest).
 * O usuário "admin" autenticado via @WithMockUser já existe de verdade no H2 com ROLE_ADMIN
 * graças a essa semeadura, o que é suficiente para o AuthorityHierarchyService.getCurrentUser().
 */
@SpringBootTest(properties = {
    "SPRING_DATASOURCE_URL=jdbc:h2:mem:usertestdb;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
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
public class UserControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private StaffMemberRepository staffMemberRepository;

    @MockitoBean
    private StudentRepository studentRepository;

    @MockitoBean
    private LoginAttemptRepository loginAttemptRepository;

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    public void testGetAllUsersSuccess() throws Exception {
        // UserService#getAllUsers() só retorna usuários de nível estritamente menor que o do
        // operador (não-root nunca vê a si mesmo nem pares/superiores na lista) — "admin" não
        // aparece no próprio retorno. Aqui validamos apenas que a chamada é bem-sucedida.
        mockMvc.perform(get("/api/v1/users").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "professor", roles = {"PROFESSOR"})
    public void testGetAllUsersAccessDeniedForProfessor() throws Exception {
        mockMvc.perform(get("/api/v1/users").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    public void testDeleteUserNotFoundReturnsStandardizedErrorBody() throws Exception {
        mockMvc.perform(delete("/api/v1/users/999999").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("Usuário não encontrado."));
    }

    // M1 da auditoria: criar, mudar roles e excluir usuário são só de administrador; a listagem segue aberta.

    private static final String[] NAO_ADMINISTRADORES = {"COORDENADOR", "SECRETARIA", "FINANCEIRO", "PROFESSOR", "ACADEMICO"};

    @Test
    public void testSomenteAdministradorCriaMudaRolesOuExcluiUsuario() throws Exception {
        for (String role : NAO_ADMINISTRADORES) {
            org.springframework.test.web.servlet.request.RequestPostProcessor as =
                    org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors
                            .user("u-" + role).roles(role);

            mockMvc.perform(multipart("/api/v1/users")
                            .param("username", "novo-" + role).param("email", role + "@example.com")
                            .param("password", "SenhaForte123!").param("roles", "ALUNO").with(as))
                    .andExpect(status().isForbidden());
            mockMvc.perform(put("/api/v1/users/1/roles").contentType(MediaType.APPLICATION_JSON)
                            .content("[\"ALUNO\"]").with(as))
                    .andExpect(status().isForbidden());
            mockMvc.perform(delete("/api/v1/users/999999").with(as))
                    .andExpect(status().isForbidden());
        }
    }

    @Test
    public void testCoordenadorESecretariaAindaPassamPelaBarreiraDaListagem() throws Exception {
        // O usuário simulado não existe no banco, então a regra de negócio da listagem pode falhar depois da
        // barreira de role; o que este teste garante é que a role continua autorizada (não é 403).
        for (String role : new String[] {"COORDENADOR", "SECRETARIA"}) {
            int httpStatus = mockMvc.perform(get("/api/v1/users").with(
                            org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors
                                    .user("u-" + role).roles(role)))
                    .andReturn().getResponse().getStatus();
            org.junit.jupiter.api.Assertions.assertNotEquals(403, httpStatus, "A listagem segue aberta a " + role);
        }
    }

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    public void testAdministradorSegueAlcancandoAsAcoesDeGovernanca() throws Exception {
        // Passa da barreira de role (não é 403): o usuário 999999 não existe, então a regra de negócio responde 404.
        mockMvc.perform(delete("/api/v1/users/999999")).andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    public void testClearAllBlocksAccessDeniedForAdmin() throws Exception {
        // Somente ROOT_MASTER pode limpar todos os bloqueios de IP; ADMIN não é suficiente aqui
        mockMvc.perform(delete("/api/v1/users/security/clear-all-blocks").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());

        verify(loginAttemptRepository, never()).deleteAll();
    }
}
