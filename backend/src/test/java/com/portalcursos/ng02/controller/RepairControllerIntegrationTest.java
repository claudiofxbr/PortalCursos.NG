package com.portalcursos.ng02.controller;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.portalcursos.ng02.model.RepairTicket;
import com.portalcursos.ng02.model.StaffMember;
import com.portalcursos.ng02.repository.RepairRepository;
import com.portalcursos.ng02.repository.StaffMemberRepository;
import com.portalcursos.ng02.service.StorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.mock.web.MockMultipartFile;
import java.util.Arrays;
import java.util.Collections;

@SpringBootTest(properties = {
    "SPRING_DATASOURCE_URL=jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
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
public class RepairControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private RepairRepository repairRepository;

    @MockBean
    private StaffMemberRepository staffMemberRepository;

    @MockBean
    private StorageService storageService;

    private RepairTicket activeTicket;
    private RepairTicket ticketWithInactiveCreator;

    @BeforeEach
    public void setUp() {
        StaffMember activeStaff = StaffMember.builder()
                .id(1L)
                .fullName("Auditor Ativo")
                .position("TÉCNICO")
                .department("INFRAESTRUTURA")
                .build();
        activeStaff.setActive(true);

        StaffMember inactiveStaff = StaffMember.builder()
                .id(2L)
                .fullName("Auditor Antigo")
                .position("ANALISTA")
                .department("TECNOLOGIA")
                .build();
        inactiveStaff.setActive(false); // Soft Deleted

        activeTicket = RepairTicket.builder()
                .id(101L)
                .title("Ar condicionado quebrado")
                .description("Lab 03 com ar condicionado sem ligar")
                .location("Lab 03, Bloco B")
                .status(RepairTicket.ERepairStatus.OPEN)
                .creator(activeStaff)
                .build();
        activeTicket.setActive(true);

        ticketWithInactiveCreator = RepairTicket.builder()
                .id(102L)
                .title("Lâmpada piscando")
                .description("Lâmpada 04 piscando intensamente")
                .location("Sala 201, Bloco A")
                .status(RepairTicket.ERepairStatus.OPEN)
                .creator(inactiveStaff) // Inativo
                .build();
        ticketWithInactiveCreator.setActive(true);
    }

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    public void testGetAllTicketsSuccess() throws Exception {
        when(repairRepository.findAllWithCreator())
                .thenReturn(Arrays.asList(activeTicket, ticketWithInactiveCreator));

        mockMvc.perform(get("/api/repairs")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size()").value(2))
                .andExpect(jsonPath("$[0].id").value(101))
                .andExpect(jsonPath("$[0].title").value("Ar condicionado quebrado"))
                .andExpect(jsonPath("$[0].creatorName").value("Auditor Ativo"))
                .andExpect(jsonPath("$[1].id").value(102))
                .andExpect(jsonPath("$[1].title").value("Lâmpada piscando"))
                // O DTO deve converter graciosamente o criador inativo para o valor legado ou default "Auditor do Sistema"
                .andExpect(jsonPath("$[1].creatorName").value("Auditor Antigo"));

        verify(repairRepository, times(1)).findAllWithCreator();
    }

    @Test
    @WithMockUser(username = "student", roles = {"ALUNO"})
    public void testGetAllTicketsAccessDeniedForStudent() throws Exception {
        mockMvc.perform(get("/api/repairs")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());
    }
    // Regressão: falha de storage no upload não deve vazar detalhes internos
    // (path de arquivo, stacktrace) na resposta ao cliente.
    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    public void testCreateTicketStoragoFailureDoesNotLeakInternalDetails() throws Exception {
        when(storageService.store(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyString()))
                .thenThrow(new java.io.IOException(
                        "Falha ao escrever em C:\\Users\\VeKTI-01\\Desktop\\uploads\\repairs-main\\segredo-interno.png: Disco cheio"));

        MockMultipartFile photo = new MockMultipartFile(
                "mainPhotoFile", "foto.png", "image/png", "conteudo-fake".getBytes());

        mockMvc.perform(multipart("/api/repairs")
                .file(photo)
                .param("title", "Falha simulada de storage")
                .param("description", "Teste de vazamento de detalhes internos")
                .param("location", "Bloco Teste"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Erro ao salvar imagem. Tente novamente."))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("C:\\Users"))))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("Disco cheio"))));
    }
}
