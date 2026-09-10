package com.portalcursos.ng02.controller;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.portalcursos.ng02.model.StaffMember;
import com.portalcursos.ng02.repository.StaffMemberRepository;
import com.portalcursos.ng02.service.StorageService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Arrays;
import java.util.Optional;

@SpringBootTest(properties = {
    "SPRING_DATASOURCE_URL=jdbc:h2:mem:staffmembertestdb;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
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
public class StaffMemberControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private StaffMemberRepository staffRepository;

    @MockitoBean
    private StorageService storageService;

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    public void testGetAllStaffSuccess() throws Exception {
        StaffMember staff = StaffMember.builder()
                .id(1L)
                .fullName("Colaborador Teste")
                .position("ANALISTA")
                .department("TI")
                .build();
        when(staffRepository.findAll()).thenReturn(Arrays.asList(staff));

        mockMvc.perform(get("/api/v1/staff").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].fullName").value("Colaborador Teste"));
    }

    @Test
    @WithMockUser(username = "secretaria", roles = {"SECRETARIA"})
    public void testGetAllStaffAccessDeniedForSecretaria() throws Exception {
        mockMvc.perform(get("/api/v1/staff").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    public void testUpdateStaffNotFoundReturns404() throws Exception {
        when(staffRepository.findById(999L)).thenReturn(Optional.empty());

        mockMvc.perform(multipart(org.springframework.http.HttpMethod.PUT, "/api/v1/staff/999")
                .param("fullName", "Inexistente")
                .param("position", "ANALISTA")
                .param("department", "TI"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Membro não encontrado."));
    }

    // Diferente de outros controllers (ex: RepairController), aqui a falha de storage
    // é engolida (log + fotoPath=null) e a criação prossegue com sucesso — documentado
    // como comportamento observado, não uma regressão a corrigir nesta rodada.
    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    public void testCreateStaffStorageFailureIsSwallowedAndDoesNotLeakInternalDetails() throws Exception {
        when(storageService.store(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq("staff-photos")))
                .thenThrow(new java.io.IOException(
                        "Falha ao escrever em C:\\Users\\VeKTI-01\\Desktop\\uploads\\staff-photos\\segredo.png: Disco cheio"));
        when(staffRepository.save(org.mockito.ArgumentMatchers.any(StaffMember.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        MockMultipartFile foto = new MockMultipartFile(
                "foto3x4File", "foto.png", "image/png", "conteudo-fake".getBytes());

        mockMvc.perform(multipart("/api/v1/staff")
                .file(foto)
                .param("fullName", "Novo Colaborador")
                .param("position", "ANALISTA")
                .param("department", "TI"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fotoUrl").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("C:\\Users"))))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("Disco cheio"))));
    }
}
