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

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    public void testClearAllBlocksAccessDeniedForAdmin() throws Exception {
        // Somente ROOT_MASTER pode limpar todos os bloqueios de IP; ADMIN não é suficiente aqui
        mockMvc.perform(delete("/api/v1/users/security/clear-all-blocks").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());

        verify(loginAttemptRepository, never()).deleteAll();
    }
}
