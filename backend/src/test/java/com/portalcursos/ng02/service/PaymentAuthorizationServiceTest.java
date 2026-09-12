package com.portalcursos.ng02.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.portalcursos.ng02.model.Payment;
import com.portalcursos.ng02.model.Student;
import com.portalcursos.ng02.model.User;
import com.portalcursos.ng02.repository.StudentRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Optional;

/**
 * Cobertura do Lote E, item F1: PaymentAuthorizationService extraído de
 * FinancialController (hasElevatedPrivileges/ownsStudentRecord/ownsPayment).
 */
@ExtendWith(MockitoExtension.class)
public class PaymentAuthorizationServiceTest {

    @Mock
    private StudentRepository studentRepository;

    @InjectMocks
    private PaymentAuthorizationService authorizationService;

    @AfterEach
    public void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private void authenticateAs(Long userId, String... roles) {
        UserDetailsImpl principal = new UserDetailsImpl(userId, "user" + userId, "user" + userId + "@test.com", "x",
                List.of(roles).stream().map(SimpleGrantedAuthority::new).toList());
        Authentication auth = new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @Test
    public void hasElevatedPrivilegesTrueParaAdmin() {
        authenticateAs(1L, "ROLE_ADMIN");
        assertTrue(authorizationService.hasElevatedPrivileges());
    }

    @Test
    public void hasElevatedPrivilegesTrueParaFinanceiro() {
        authenticateAs(1L, "ROLE_FINANCEIRO");
        assertTrue(authorizationService.hasElevatedPrivileges());
    }

    @Test
    public void hasElevatedPrivilegesFalseParaAluno() {
        authenticateAs(1L, "ROLE_ALUNO");
        assertFalse(authorizationService.hasElevatedPrivileges());
    }

    @Test
    public void hasElevatedPrivilegesFalseSemAutenticacao() {
        SecurityContextHolder.getContext().setAuthentication(
                new TestingAuthenticationToken(null, null));
        assertFalse(authorizationService.hasElevatedPrivileges());
    }

    @Test
    public void ownsStudentRecordTrueQuandoStudentIdBateComUsuarioLogado() {
        authenticateAs(7L, "ROLE_ALUNO");
        Student student = Student.builder().id(55L).build();
        when(studentRepository.findByUserId(7L)).thenReturn(Optional.of(student));

        assertTrue(authorizationService.ownsStudentRecord(55L));
    }

    @Test
    public void ownsStudentRecordFalseQuandoStudentIdNaoBate() {
        authenticateAs(7L, "ROLE_ALUNO");
        Student student = Student.builder().id(55L).build();
        when(studentRepository.findByUserId(7L)).thenReturn(Optional.of(student));

        assertFalse(authorizationService.ownsStudentRecord(99L));
    }

    @Test
    public void ownsStudentRecordFalseQuandoUsuarioNaoTemStudentVinculado() {
        authenticateAs(7L, "ROLE_ALUNO");
        when(studentRepository.findByUserId(7L)).thenReturn(Optional.empty());

        assertFalse(authorizationService.ownsStudentRecord(55L));
    }

    @Test
    public void ownsPaymentTrueQuandoPaymentPertenceAoUsuarioLogado() {
        authenticateAs(7L, "ROLE_ALUNO");
        User owner = User.builder().id(7L).username("aluno7").email("aluno7@test.com").password("x").build();
        Student student = Student.builder().id(55L).user(owner).build();
        Payment payment = Payment.builder().id(1L).student(student).build();

        assertTrue(authorizationService.ownsPayment(payment));
    }

    @Test
    public void ownsPaymentFalseQuandoPaymentPertenceAOutroUsuario() {
        authenticateAs(7L, "ROLE_ALUNO");
        User owner = User.builder().id(99L).username("aluno99").email("aluno99@test.com").password("x").build();
        Student student = Student.builder().id(55L).user(owner).build();
        Payment payment = Payment.builder().id(1L).student(student).build();

        assertFalse(authorizationService.ownsPayment(payment));
    }

    @Test
    public void ownsPaymentFalseQuandoPaymentSemStudent() {
        authenticateAs(7L, "ROLE_ALUNO");
        Payment payment = Payment.builder().id(1L).build();

        assertFalse(authorizationService.ownsPayment(payment));
    }
}
