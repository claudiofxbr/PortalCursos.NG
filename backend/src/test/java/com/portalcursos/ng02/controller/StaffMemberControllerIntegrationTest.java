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
        when(staffRepository.findAllByActiveTrue()).thenReturn(Arrays.asList(staff));

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

    // Mesmo padrão de RepairController: falha de storage vira BusinessException (400),
    // sem engolir o erro nem deixar o registro com fotoUrl=null silenciosamente.
    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    public void testCreateStaffStorageFailurePropagatesAsBusinessExceptionAndDoesNotLeakInternalDetails() throws Exception {
        when(storageService.store(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq("staff-photos")))
                .thenThrow(new java.io.IOException(
                        "Falha ao escrever em C:\\Users\\VeKTI-01\\Desktop\\uploads\\staff-photos\\segredo.png: Disco cheio"));

        MockMultipartFile foto = new MockMultipartFile(
                "foto3x4File", "foto.png", "image/png", "conteudo-fake".getBytes());

        mockMvc.perform(multipart("/api/v1/staff")
                .file(foto)
                .param("fullName", "Novo Colaborador")
                .param("position", "ANALISTA")
                .param("department", "TI"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Erro ao salvar imagem. Tente novamente."))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("C:\\Users"))))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("Disco cheio"))));

        verify(staffRepository, never()).save(org.mockito.ArgumentMatchers.any(StaffMember.class));
    }

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    public void testUpdateStaffStorageFailurePropagatesAsBusinessException() throws Exception {
        StaffMember staff = StaffMember.builder()
                .id(1L)
                .fullName("Colaborador Teste")
                .position("ANALISTA")
                .department("TI")
                .active(true)
                .build();
        when(staffRepository.findById(1L)).thenReturn(Optional.of(staff));
        when(storageService.store(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq("staff-photos")))
                .thenThrow(new java.io.IOException("Disco cheio"));

        MockMultipartFile foto = new MockMultipartFile(
                "foto3x4File", "foto.png", "image/png", "conteudo-fake".getBytes());

        mockMvc.perform(multipart(org.springframework.http.HttpMethod.PUT, "/api/v1/staff/1")
                .file(foto)
                .param("fullName", "Colaborador Teste")
                .param("position", "ANALISTA")
                .param("department", "TI"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Erro ao salvar imagem. Tente novamente."));

        verify(staffRepository, never()).save(org.mockito.ArgumentMatchers.any(StaffMember.class));
    }

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    public void testUpdateStaffInactiveBlockedWithBusinessException() throws Exception {
        StaffMember staff = StaffMember.builder()
                .id(2L)
                .fullName("Colaborador Desativado")
                .position("ANALISTA")
                .department("TI")
                .active(false)
                .build();
        when(staffRepository.findById(2L)).thenReturn(Optional.of(staff));

        mockMvc.perform(multipart(org.springframework.http.HttpMethod.PUT, "/api/v1/staff/2")
                .param("fullName", "Nova Tentativa de Edição")
                .param("position", "ANALISTA")
                .param("department", "TI"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Colaborador está desativado — reative antes de editar."));

        verify(staffRepository, never()).save(org.mockito.ArgumentMatchers.any(StaffMember.class));
    }
}
