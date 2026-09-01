package com.portalcursos.ng02.controller;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.portalcursos.ng02.service.StorageService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

/**
 * BUG CORRIGIDO em DocumentController#extractRelativePath(): PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE
 * não removia o prefixo do mapeamento quando o padrão é "/**", fazendo category resolver sempre
 * para "" e todo acesso a /api/uploads/** retornar 403. Corrigido usando AntPathMatcher para
 * extrair a porção do path que casou com "**". Ver DocumentController.java.
 */
@SpringBootTest(properties = {
    "SPRING_DATASOURCE_URL=jdbc:h2:mem:documenttestdb;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
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
public class DocumentControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private StorageService storageService;

    @Test
    @WithMockUser(username = "aluno", roles = {"ALUNO"})
    public void testServeStaffPhotoSuccessForAnyAuthenticatedUser() throws Exception {
        Resource resource = new ByteArrayResource("conteudo-fake".getBytes());
        when(storageService.loadAsResource("staff-photos/foto.png")).thenReturn(resource);

        mockMvc.perform(get("/api/uploads/staff-photos/foto.png"))
                .andExpect(status().isOk());
    }

    // Continua passando porque o resultado esperado (403) coincide com o bug (category sempre
    // vazia também nega acesso) — não é uma confirmação real de que o controle por role funciona.
    @Test
    @WithMockUser(username = "aluno", roles = {"ALUNO"})
    public void testServeGradStudentDocumentDeniedForAluno() throws Exception {
        mockMvc.perform(get("/api/uploads/grad-students/rg/doc.pdf"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));

        verify(storageService, never()).loadAsResource(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    public void testServeFileNotFoundDoesNotLeakInternalDetails() throws Exception {
        when(storageService.loadAsResource("staff-photos/inexistente.png"))
                .thenThrow(new java.io.IOException(
                        "Arquivo não encontrado: C:\\Users\\VeKTI-01\\Desktop\\uploads\\staff-photos\\segredo.png"));

        mockMvc.perform(get("/api/uploads/staff-photos/inexistente.png"))
                .andExpect(status().isNotFound())
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("C:\\Users"))));
    }
}
