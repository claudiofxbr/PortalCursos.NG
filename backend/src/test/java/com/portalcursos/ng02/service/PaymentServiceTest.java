package com.portalcursos.ng02.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.portalcursos.ng02.dto.ManualChargeRequest;
import com.portalcursos.ng02.exception.ResourceNotFoundException;
import com.portalcursos.ng02.model.EAcademicLevel;
import com.portalcursos.ng02.model.EPaymentStatus;
import com.portalcursos.ng02.model.Payment;
import com.portalcursos.ng02.model.PostgradStudent;
import com.portalcursos.ng02.model.Student;
import com.portalcursos.ng02.repository.PaymentRepository;
import com.portalcursos.ng02.repository.PostgradStudentRepository;
import com.portalcursos.ng02.repository.StudentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

/**
 * Cobertura do Lote E, item F2: PaymentService extraído de FinancialController
 * (createManualCharge/updateCharge/deleteCharge).
 */
@ExtendWith(MockitoExtension.class)
public class PaymentServiceTest {

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private StudentRepository studentRepository;

    @Mock
    private PostgradStudentRepository postgradStudentRepository;

    @Mock
    private AuditService auditService;

    @InjectMocks
    private PaymentService paymentService;

    private ManualChargeRequest requestFor(EAcademicLevel level, Long studentId) {
        ManualChargeRequest request = new ManualChargeRequest();
        request.setAmount(new BigDecimal("250.00"));
        request.setDueDate(LocalDate.now().plusDays(10));
        request.setAcademicLevel(level);
        request.setStudentId(studentId);
        request.setDescription("Mensalidade teste");
        return request;
    }

    @Test
    public void createManualChargeGraduacaoVinculaStudentEFoto() {
        Student student = Student.builder().id(1L).fotoMatricula("foto-grad.jpg").build();
        when(studentRepository.findById(1L)).thenReturn(Optional.of(student));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));

        Payment result = paymentService.createManualCharge(requestFor(EAcademicLevel.GRADUATION, 1L));

        assertEquals(student, result.getStudent());
        assertEquals("foto-grad.jpg", result.getStudentPhotoUrl());
        assertEquals(EPaymentStatus.PENDING, result.getStatus());
        verify(auditService).injectCreator(result);
        verify(postgradStudentRepository, never()).findById(any());
    }

    @Test
    public void createManualChargeGraduacaoComStudentInexistenteLanca404() {
        when(studentRepository.findById(99L)).thenReturn(Optional.empty());

        ResourceNotFoundException ex = assertThrows(ResourceNotFoundException.class,
                () -> paymentService.createManualCharge(requestFor(EAcademicLevel.GRADUATION, 99L)));

        assertEquals("Estudante de graduação não encontrado", ex.getMessage());
        verify(paymentRepository, never()).save(any());
    }

    @Test
    public void createManualChargePosGraduacaoVinculaPostgradStudent() {
        PostgradStudent postgrad = PostgradStudent.builder().id(2L).fotoMatricula("foto-pos.jpg").build();
        when(postgradStudentRepository.findById(2L)).thenReturn(Optional.of(postgrad));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));

        Payment result = paymentService.createManualCharge(requestFor(EAcademicLevel.POSTGRADUATE, 2L));

        assertEquals(postgrad, result.getStudent());
        assertEquals("foto-pos.jpg", result.getStudentPhotoUrl());
        verify(studentRepository, never()).findById(any());
    }

    @Test
    public void createManualChargePosGraduacaoComStudentInexistenteLanca404() {
        when(postgradStudentRepository.findById(88L)).thenReturn(Optional.empty());

        ResourceNotFoundException ex = assertThrows(ResourceNotFoundException.class,
                () -> paymentService.createManualCharge(requestFor(EAcademicLevel.POSTGRADUATE, 88L)));

        assertEquals("Estudante de pós-graduação não encontrado", ex.getMessage());
        verify(paymentRepository, never()).save(any());
    }

    @Test
    public void updateChargeAtualizaCamposEPersiste() {
        Payment existing = Payment.builder().id(5L).amount(new BigDecimal("100.00")).build();
        when(paymentRepository.findByIdWithCreatorAndStudent(5L)).thenReturn(Optional.of(existing));
        when(paymentRepository.save(existing)).thenReturn(existing);

        ManualChargeRequest request = requestFor(EAcademicLevel.GRADUATION, 1L);
        request.setAmount(new BigDecimal("300.00"));

        Payment result = paymentService.updateCharge(5L, request);

        assertEquals(new BigDecimal("300.00"), result.getAmount());
        verify(auditService).injectCreator(existing);
    }

    @Test
    public void updateChargeInexistenteLanca404() {
        when(paymentRepository.findByIdWithCreatorAndStudent(404L)).thenReturn(Optional.empty());

        ResourceNotFoundException ex = assertThrows(ResourceNotFoundException.class,
                () -> paymentService.updateCharge(404L, requestFor(EAcademicLevel.GRADUATION, 1L)));

        assertEquals("Cobrança não encontrada", ex.getMessage());
        verify(paymentRepository, never()).save(any());
    }

    @Test
    public void deleteChargeRemoveQuandoExiste() {
        Payment existing = Payment.builder().id(7L).build();
        when(paymentRepository.findById(7L)).thenReturn(Optional.of(existing));

        paymentService.deleteCharge(7L);

        verify(paymentRepository).delete(existing);
    }

    @Test
    public void deleteChargeInexistenteLanca404() {
        when(paymentRepository.findById(404L)).thenReturn(Optional.empty());

        ResourceNotFoundException ex = assertThrows(ResourceNotFoundException.class,
                () -> paymentService.deleteCharge(404L));

        assertEquals("Cobrança não encontrada", ex.getMessage());
        verify(paymentRepository, never()).delete(any());
    }
}
