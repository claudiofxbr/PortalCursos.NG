package com.portalcursos.ng02.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.portalcursos.ng02.model.EPaymentStatus;
import com.portalcursos.ng02.model.Payment;
import com.portalcursos.ng02.model.Student;
import com.portalcursos.ng02.repository.PaymentRepository;
import com.portalcursos.ng02.repository.StudentRepository;
import com.portalcursos.ng02.service.UserDetailsImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Exclusão e perdão de cobrança só com autorização explícita de administrador, de ponta a ponta (usuário real no
 * contexto de segurança, serviço real, banco H2): excluir a fatura, reduzir ou zerar o valor e lançar valor zero
 * digitado. Quem não é administrador recebe 403 e a cobrança não muda.
 */
@SpringBootTest(properties = {
    "SPRING_DATASOURCE_URL=jdbc:h2:mem:chargeforgivenesstestdb;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
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
@Transactional
class ChargeForgivenessAdminOnlyIntegrationTest {

    private static final String JSON = MediaType.APPLICATION_JSON_VALUE;

    @Autowired private MockMvc mockMvc;
    @Autowired private PaymentRepository paymentRepository;
    @Autowired private StudentRepository studentRepository;

    private Long studentId;
    private Long invoiceId;

    @BeforeEach
    void seed() {
        Student student = Student.builder().registrationNumber("GRAD-PERDAO-1").fullName("Aluno Perdão")
                .email("perdao@example.com").cpf("39012874600").build();
        studentId = studentRepository.saveAndFlush(student).getId();
        Payment p = Payment.builder().amount(new BigDecimal("100.00")).dueDate(LocalDate.now().plusDays(10))
                .status(EPaymentStatus.PENDING).student(student).build();
        invoiceId = paymentRepository.saveAndFlush(p).getId();
    }

    private static RequestPostProcessor as(String role) {
        return user(new UserDetailsImpl(7L, "u-" + role, "u@x.com", "x", List.of(new SimpleGrantedAuthority("ROLE_" + role))));
    }

    private String edit(String amount) {
        return "{\"amount\":" + amount + ",\"dueDate\":\"2026-12-01\",\"category\":\"TUITION\",\"description\":\"editada\"}";
    }

    private String create(String amount) {
        return "{\"studentId\":" + studentId + ",\"academicLevel\":\"GRADUATION\",\"category\":\"TUITION\",\"amount\":" + amount
                + ",\"dueDate\":\"2026-12-01\",\"description\":\"manual\"}";
    }

    // --- excluir ---

    @Test
    void secretariaEFinanceiroNaoExcluemCobranca() throws Exception {
        for (String role : List.of("SECRETARIA", "FINANCEIRO")) {
            mockMvc.perform(delete("/api/finance/invoices/" + invoiceId).with(as(role))).andExpect(status().isForbidden());
        }
        assertTrue(paymentRepository.findById(invoiceId).orElseThrow().isActive(), "a cobrança continua ativa");
    }

    @Test
    void administradorEOrootMasterExcluemEACobrancaFicaInativa() throws Exception {
        mockMvc.perform(delete("/api/finance/invoices/" + invoiceId).with(as("ADMIN"))).andExpect(status().isOk());
        assertEquals(false, paymentRepository.findById(invoiceId).map(Payment::isActive).orElse(false));
    }

    // --- reduzir ou zerar o valor ---

    @Test
    void secretariaEFinanceiroNaoReduzemNemZeramOValorDeCobrancaLancada() throws Exception {
        for (String role : List.of("SECRETARIA", "FINANCEIRO")) {
            mockMvc.perform(put("/api/finance/invoices/" + invoiceId).with(as(role)).contentType(JSON).content(edit("40.00")))
                    .andExpect(status().isForbidden());
            mockMvc.perform(put("/api/finance/invoices/" + invoiceId).with(as(role)).contentType(JSON).content(edit("0.00")))
                    .andExpect(status().isForbidden());
        }
        Payment unchanged = paymentRepository.findById(invoiceId).orElseThrow();
        assertEquals(0, unchanged.getAmount().compareTo(new BigDecimal("100.00")));
        assertEquals(EPaymentStatus.PENDING, unchanged.getStatus());
    }

    @Test
    void secretariaAindaEditaDescricaoEVencimentoSemMudarOValor() throws Exception {
        mockMvc.perform(put("/api/finance/invoices/" + invoiceId).with(as("SECRETARIA")).contentType(JSON).content(edit("100.00")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.description").value("editada"));
    }

    @Test
    void administradorAlteraOValorComARegraEmEspera() throws Exception {
        mockMvc.perform(put("/api/finance/invoices/" + invoiceId).with(as("ADMIN")).contentType(JSON).content(edit("80.00")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.amount").value(80.0));
    }

    // --- lançar valor zero digitado ---

    @Test
    void naoAdministradorNaoLancaCobrancaManualDeValorZeroMasLancaValorPositivo() throws Exception {
        mockMvc.perform(post("/api/finance/charge").with(as("FINANCEIRO")).contentType(JSON).content(create("0.00")))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/finance/charge").with(as("FINANCEIRO")).contentType(JSON).content(create("250.00")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    void administradorPodeLancarValorZeroEElaNasceQuitada() throws Exception {
        mockMvc.perform(post("/api/finance/charge").with(as("ADMIN")).contentType(JSON).content(create("0.00")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAID"));
    }

    @Test
    void valorNegativoEhRecusadoAteParaAdministrador() throws Exception {
        mockMvc.perform(post("/api/finance/charge").with(as("ADMIN")).contentType(JSON).content(create("-5.00")))
                .andExpect(status().isBadRequest());
    }
}
