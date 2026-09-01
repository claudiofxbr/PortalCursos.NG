package com.portalcursos.ng02.controller;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.portalcursos.ng02.model.EPaymentStatus;
import com.portalcursos.ng02.model.Payment;
import com.portalcursos.ng02.repository.PaymentRepository;
import com.portalcursos.ng02.repository.PostgradStudentRepository;
import com.portalcursos.ng02.repository.StaffMemberRepository;
import com.portalcursos.ng02.repository.StudentRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

@SpringBootTest(properties = {
    "SPRING_DATASOURCE_URL=jdbc:h2:mem:financetestdb;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
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
public class FinancialControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PaymentRepository paymentRepository;

    @MockBean
    private StudentRepository studentRepository;

    @MockBean
    private PostgradStudentRepository postgradStudentRepository;

    @MockBean
    private StaffMemberRepository staffMemberRepository;

    @Test
    @WithMockUser(username = "financeiro", roles = {"FINANCEIRO"})
    public void testGetInvoicesSuccess() throws Exception {
        Payment payment = Payment.builder()
                .id(10L)
                .amount(new BigDecimal("500.00"))
                .dueDate(LocalDate.now().plusDays(5))
                .status(EPaymentStatus.PENDING)
                .build();
        when(paymentRepository.findByStatusIn(anyList())).thenReturn(Arrays.asList(payment));

        mockMvc.perform(get("/api/finance/invoices").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(10));
    }

    @Test
    @WithMockUser(username = "aluno", roles = {"ALUNO"})
    public void testGetInvoicesAccessDeniedForAluno() throws Exception {
        // /invoices (sem escopo) só permite roles operacionais/administrativas, não ALUNO
        mockMvc.perform(get("/api/finance/invoices").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "financeiro", roles = {"FINANCEIRO"})
    public void testCreateManualChargeStudentNotFoundReturns404() throws Exception {
        when(studentRepository.findById(999L)).thenReturn(Optional.empty());

        String payload = "{"
                + "\"amount\":100.00,"
                + "\"dueDate\":\"2026-12-01\","
                + "\"studentId\":999,"
                + "\"academicLevel\":\"GRADUATION\","
                + "\"category\":\"TUITION\","
                + "\"description\":\"Mensalidade teste\""
                + "}";

        mockMvc.perform(post("/api/finance/charge")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("Estudante de graduação não encontrado"));
    }

    @Test
    @WithMockUser(username = "aluno", roles = {"ALUNO"})
    public void testGetStudentPaymentsForbiddenWhenNotOwner() throws Exception {
        // Aluno autenticado sem vínculo com o studentId solicitado (não é ownership nem tem privilégio elevado)
        when(studentRepository.findByUserId(org.mockito.ArgumentMatchers.anyLong())).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/finance/student/55").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value(
                        "Acesso negado: Você só pode visualizar seus próprios dados financeiros."));
    }
}
