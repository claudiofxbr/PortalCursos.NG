package com.portalcursos.ng02.controller;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;

import com.portalcursos.ng02.model.EAcademicLevel;
import com.portalcursos.ng02.model.EPaymentStatus;
import com.portalcursos.ng02.model.Payment;
import com.portalcursos.ng02.model.Student;
import com.portalcursos.ng02.model.User;
import com.portalcursos.ng02.repository.PaymentRepository;
import com.portalcursos.ng02.repository.PostgradStudentRepository;
import com.portalcursos.ng02.repository.StudentRepository;
import com.portalcursos.ng02.service.UserDetailsImpl;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;

/**
 * Cobre a correção de IDOR no FinancialController: um ALUNO só pode ver/gerar
 * pagamentos do próprio registro de estudante, nunca de outro aluno ou do nível
 * acadêmico inteiro.
 */
@SpringBootTest(properties = {
    "SPRING_DATASOURCE_URL=jdbc:h2:mem:financialtestdb;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
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

    private org.springframework.security.core.Authentication authFor(Long userId, String username) {
        User user = User.builder().id(userId).username(username).email(username + "@test.com")
                .password("x").roles(java.util.Collections.emptySet()).build();
        UserDetailsImpl principal = UserDetailsImpl.build(user);
        return new UsernamePasswordAuthenticationToken(
                principal, null, List.of(new SimpleGrantedAuthority("ROLE_ALUNO")));
    }

    @Test
    public void alunoNaoPodeVerFaturasDeOutroAluno() throws Exception {
        Student owner = Student.builder().id(10L).build();
        when(studentRepository.findByUserId(1L)).thenReturn(Optional.of(owner));

        mockMvc.perform(get("/api/finance/student/{studentId}", 99L)
                .with(authentication(authFor(1L, "aluno1")))
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());

        verify(paymentRepository, never()).findByStudentId(anyLong());
    }

    @Test
    public void alunoPodeVerAsProprioFaturas() throws Exception {
        Student owner = Student.builder().id(10L).build();
        when(studentRepository.findByUserId(1L)).thenReturn(Optional.of(owner));
        when(paymentRepository.findByStudentId(10L)).thenReturn(List.of());

        mockMvc.perform(get("/api/finance/student/{studentId}", 10L)
                .with(authentication(authFor(1L, "aluno1")))
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());

        verify(paymentRepository, times(1)).findByStudentId(10L);
    }

    @Test
    public void alunoNaoPodeListarFaturasDeTodoONivel() throws Exception {
        mockMvc.perform(get("/api/finance/invoices/{level}", "graduation")
                .with(authentication(authFor(1L, "aluno1")))
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());

        verify(paymentRepository, never()).findByAcademicLevelAndStatusIn(any(), any());
    }

    @Test
    public void alunoNaoPodeGerarPixDeFaturaDeOutroAluno() throws Exception {
        Student owner = Student.builder().id(10L).build();
        Student other = Student.builder().id(20L).build();
        User otherUser = User.builder().id(2L).username("aluno2").email("a2@test.com")
                .password("x").roles(java.util.Collections.emptySet()).build();
        other.setUser(otherUser);

        Payment payment = Payment.builder().id(500L).student(other)
                .amount(java.math.BigDecimal.TEN)
                .status(EPaymentStatus.PENDING)
                .academicLevel(EAcademicLevel.GRADUATION)
                .build();

        when(studentRepository.findByUserId(1L)).thenReturn(Optional.of(owner));
        when(paymentRepository.findByIdWithCreatorAndStudent(500L)).thenReturn(Optional.of(payment));

        mockMvc.perform(post("/api/finance/generate-pix/{paymentId}", 500L)
                .with(authentication(authFor(1L, "aluno1")))
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());

        verify(paymentRepository, never()).save(any());
    }
}
