package com.portalcursos.ng02.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.portalcursos.ng02.dto.CardCheckoutResult;
import com.portalcursos.ng02.model.EPaymentStatus;
import com.portalcursos.ng02.model.Payment;
import com.portalcursos.ng02.repository.PaymentRepository;
import com.portalcursos.ng02.repository.PostgradStudentRepository;
import com.portalcursos.ng02.repository.StaffMemberRepository;
import com.portalcursos.ng02.repository.StudentRepository;
import com.portalcursos.ng02.service.PagBankCheckoutGatewayService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

/**
 * Fronteira HTTP do POST /api/finance/generate-card-checkout/{id}. O PagBank é sempre mockado.
 * Ambiente padrão dos testes = sandbox (pagbank.env ausente): só perfil elevado paga.
 */
@SpringBootTest(properties = {
    "SPRING_DATASOURCE_URL=jdbc:h2:mem:cardcheckouttestdb;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
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
class CardCheckoutControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PaymentRepository paymentRepository;

    @MockitoBean
    private StudentRepository studentRepository;

    @MockitoBean
    private PostgradStudentRepository postgradStudentRepository;

    @MockitoBean
    private StaffMemberRepository staffMemberRepository;

    @MockitoBean
    private PagBankCheckoutGatewayService checkoutGateway;

    private Payment pending(long id) {
        return Payment.builder()
                .id(id)
                .amount(new BigDecimal("199.90"))
                .dueDate(LocalDate.now().plusDays(5))
                .status(EPaymentStatus.PENDING)
                .build();
    }

    @Test
    @WithMockUser(username = "financeiro", roles = {"FINANCEIRO"})
    void perfilElevadoCriaCheckoutENuncaDevolveAprovado() throws Exception {
        Payment p = pending(10L);
        when(paymentRepository.findByIdWithCreatorAndStudent(10L)).thenReturn(Optional.of(p));
        when(checkoutGateway.createCardCheckout(eq(p), any(), any(), anyBoolean()))
                .thenReturn(new CardCheckoutResult("CHEC_TEST1", "https://pagamento.example/pay", 19990L));

        mockMvc.perform(post("/api/finance/generate-card-checkout/10").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.url").value("https://pagamento.example/pay"))
                .andExpect(jsonPath("$.paymentId").value(10))
                .andExpect(jsonPath("$.reused").value(false))
                .andExpect(jsonPath("$.status").doesNotExist());

        verify(paymentRepository).save(p);
    }

    @Test
    @WithMockUser(username = "aluno", roles = {"ALUNO"})
    void alunoQueNaoEDonoDaFaturaRecebe403SemChamarOPsp() throws Exception {
        when(paymentRepository.findByIdWithCreatorAndStudent(11L)).thenReturn(Optional.of(pending(11L)));

        mockMvc.perform(post("/api/finance/generate-card-checkout/11").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Acesso negado: esta fatura não pertence a você."));

        verifyNoInteractions(checkoutGateway);
        verify(paymentRepository, never()).save(any());
    }

    @Test
    @WithMockUser(username = "financeiro", roles = {"FINANCEIRO"})
    void faturaInexistenteRetorna404() throws Exception {
        when(paymentRepository.findByIdWithCreatorAndStudent(999L)).thenReturn(Optional.empty());

        mockMvc.perform(post("/api/finance/generate-card-checkout/999").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(username = "financeiro", roles = {"FINANCEIRO"})
    void faturaPagaRetorna400() throws Exception {
        Payment paid = pending(12L);
        paid.setStatus(EPaymentStatus.PAID);
        when(paymentRepository.findByIdWithCreatorAndStudent(12L)).thenReturn(Optional.of(paid));

        mockMvc.perform(post("/api/finance/generate-card-checkout/12").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(checkoutGateway);
    }

    @Test
    void semAutenticacaoNaoPassa() throws Exception {
        mockMvc.perform(post("/api/finance/generate-card-checkout/10").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().is4xxClientError());

        verifyNoInteractions(checkoutGateway);
    }
}
