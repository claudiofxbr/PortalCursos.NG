package com.portalcursos.ng02.controller;

import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.portalcursos.ng02.model.DataDeletionRequest;
import com.portalcursos.ng02.model.User;
import com.portalcursos.ng02.repository.DataDeletionRequestRepository;
import com.portalcursos.ng02.repository.StudentRepository;
import com.portalcursos.ng02.repository.UserRepository;
import com.portalcursos.ng02.service.DataAnonymizationService;
import com.portalcursos.ng02.service.StorageService;
import com.portalcursos.ng02.service.UserDetailsImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Regressão para os endpoints de autoatendimento LGPD/GDPR (PrivacyController) — sem nenhuma
 * cobertura antes, apesar de lidar diretamente com dado pessoal e eliminação definitiva.
 */
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
public class PrivacyControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UserRepository userRepository;

    @MockBean
    private StudentRepository studentRepository;

    @MockBean
    private DataDeletionRequestRepository deletionRequestRepository;

    @MockBean
    private DataAnonymizationService anonymizationService;

    // StorageService não é usado diretamente pelo PrivacyController, mas o
    // DataAnonymizationService real (não mockado nos outros testes deste módulo) depende
    // dele — mantém o contexto Spring consistente com os demais testes de integração.
    @MockBean
    private StorageService storageService;

    private User user;

    @BeforeEach
    public void setUp() {
        user = User.builder()
                .id(1L)
                .username("aluno1")
                .email("aluno1@example.com")
                .password("hash-irrelevante")
                .build();
        when(userRepository.findByUsername("aluno1")).thenReturn(Optional.of(user));
        when(studentRepository.findByUserId(1L)).thenReturn(Optional.empty());
    }

    @Test
    public void getMyDataReturnsOwnAccountData() throws Exception {
        // PrivacyController#currentUser() faz cast direto pra UserDetailsImpl (não é o tipo
        // default que @WithMockUser cria) — precisa do principal real via
        // SecurityMockMvcRequestPostProcessors.user(), não @WithMockUser.
        mockMvc.perform(get("/api/privacy/my-data").with(user(UserDetailsImpl.build(user))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.account.id").value(1))
                .andExpect(jsonPath("$.account.username").value("aluno1"))
                .andExpect(jsonPath("$.account.email").value("aluno1@example.com"));
    }

    @Test
    public void getMyDataRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/privacy/my-data"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    public void requestDeletionSucceedsWhenNoPendingRequestExists() throws Exception {
        when(deletionRequestRepository.findFirstByUserIdAndStatus(1L, DataDeletionRequest.Status.PENDING))
                .thenReturn(Optional.empty());

        mockMvc.perform(post("/api/privacy/data-deletion-request").with(user(UserDetailsImpl.build(user))))
                .andExpect(status().isOk());

        verify(deletionRequestRepository, times(1)).save(any(DataDeletionRequest.class));
    }

    @Test
    public void requestDeletionRejectsDuplicatePendingRequest() throws Exception {
        DataDeletionRequest existing = DataDeletionRequest.builder()
                .id(99L)
                .userId(1L)
                .status(DataDeletionRequest.Status.PENDING)
                .build();
        when(deletionRequestRepository.findFirstByUserIdAndStatus(1L, DataDeletionRequest.Status.PENDING))
                .thenReturn(Optional.of(existing));

        mockMvc.perform(post("/api/privacy/data-deletion-request").with(user(UserDetailsImpl.build(user))))
                .andExpect(status().isBadRequest());

        verify(deletionRequestRepository, never()).save(any(DataDeletionRequest.class));
    }

    @Test
    @WithMockUser(username = "aluno1", roles = {"ALUNO"})
    public void listDeletionRequestsForbiddenForNonAdmin() throws Exception {
        mockMvc.perform(get("/api/privacy/data-deletion-requests"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    public void listDeletionRequestsAllowedForAdmin() throws Exception {
        when(deletionRequestRepository.findAllByOrderByRequestedAtDesc()).thenReturn(List.of());

        mockMvc.perform(get("/api/privacy/data-deletion-requests"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    public void executeDeletionRejectsRequestNotYetApproved() throws Exception {
        DataDeletionRequest pending = DataDeletionRequest.builder()
                .id(5L)
                .userId(1L)
                .status(DataDeletionRequest.Status.PENDING)
                .build();
        when(deletionRequestRepository.findById(5L)).thenReturn(Optional.of(pending));

        mockMvc.perform(post("/api/privacy/data-deletion-requests/5/execute"))
                .andExpect(status().isBadRequest());

        verify(anonymizationService, never()).anonymize(any());
    }

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    public void executeDeletionReturnsConflictWhenBlockedByRetentionPeriod() throws Exception {
        DataDeletionRequest approved = DataDeletionRequest.builder()
                .id(6L)
                .userId(1L)
                .status(DataDeletionRequest.Status.APPROVED)
                .build();
        when(deletionRequestRepository.findById(6L)).thenReturn(Optional.of(approved));
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(anonymizationService.anonymize(user)).thenReturn(
                DataAnonymizationService.AnonymizationResult.blocked(
                        LocalDate.now().plusYears(3), "Dentro do prazo legal de guarda."));

        mockMvc.perform(post("/api/privacy/data-deletion-requests/6/execute"))
                .andExpect(status().isConflict());

        verify(deletionRequestRepository, never()).save(argThat(r -> r.getStatus() == DataDeletionRequest.Status.COMPLETED));
    }

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    public void executeDeletionCompletesWhenApprovedAndEligible() throws Exception {
        DataDeletionRequest approved = DataDeletionRequest.builder()
                .id(7L)
                .userId(1L)
                .status(DataDeletionRequest.Status.APPROVED)
                .build();
        when(deletionRequestRepository.findById(7L)).thenReturn(Optional.of(approved));
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(anonymizationService.anonymize(user)).thenReturn(DataAnonymizationService.AnonymizationResult.ok());

        mockMvc.perform(post("/api/privacy/data-deletion-requests/7/execute"))
                .andExpect(status().isOk());

        verify(deletionRequestRepository, times(1))
                .save(argThat(r -> r.getStatus() == DataDeletionRequest.Status.COMPLETED));
    }
}
