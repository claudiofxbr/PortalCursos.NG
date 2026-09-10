package com.portalcursos.ng02.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.portalcursos.ng02.model.Payment;
import com.portalcursos.ng02.model.Student;
import com.portalcursos.ng02.model.StudentDocument;
import com.portalcursos.ng02.model.User;
import com.portalcursos.ng02.repository.PaymentRepository;
import com.portalcursos.ng02.repository.StudentRepository;
import com.portalcursos.ng02.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Cobertura do fluxo de eliminação definitiva de dados (LGPD art. 18 / GDPR art. 17):
 * este service não tinha nenhum teste apesar de mexer diretamente em dado pessoal.
 */
@ExtendWith(MockitoExtension.class)
public class DataAnonymizationServiceTest {

    @Mock
    private StudentRepository studentRepository;

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private StorageService storageService;

    @InjectMocks
    private DataAnonymizationService anonymizationService;

    private User baseUser;

    @BeforeEach
    public void setUp() {
        baseUser = User.builder()
                .id(1L)
                .username("aluno.teste")
                .email("aluno.teste@example.com")
                .password("hash-irrelevante")
                .build();
    }

    @Test
    public void anonymizeWithoutStudentRecordOnlyAnonymizesUser() {
        when(studentRepository.findByUserId(1L)).thenReturn(Optional.empty());

        DataAnonymizationService.AnonymizationResult result = anonymizationService.anonymize(baseUser);

        assertTrue(result.completed());
        verify(userRepository, times(1)).save(any(User.class));
        verify(studentRepository, never()).save(any(Student.class));
        assertNotEquals("aluno.teste", baseUser.getUsername());
        assertTrue(baseUser.getUsername().startsWith("usuario-anonimizado-"));
        assertTrue(baseUser.getRoles().isEmpty());
    }

    @Test
    public void anonymizeBlockedWhileWithinAcademicRetentionPeriod() {
        Student recentStudent = Student.builder()
                .id(10L)
                .fullName("Aluno Recente")
                .cpf("111.111.111-11")
                .updatedAt(LocalDateTime.now().minusYears(1)) // muito recente: 20 anos de guarda
                .documents(new ArrayList<>())
                .build();

        when(studentRepository.findByUserId(1L)).thenReturn(Optional.of(recentStudent));
        when(paymentRepository.findByStudentId(10L)).thenReturn(List.of());

        DataAnonymizationService.AnonymizationResult result = anonymizationService.anonymize(baseUser);

        assertFalse(result.completed());
        assertNotNull(result.earliestEligibleDate());
        assertTrue(result.earliestEligibleDate().isAfter(LocalDate.now()));
        verify(studentRepository, never()).save(any());
        verify(userRepository, never()).save(any());
    }

    @Test
    public void anonymizeBlockedWhileWithinFinancialRetentionPeriod() {
        // Cadastro acadêmico já elegível (21 anos), mas pagamento recente ainda dentro
        // dos 5 anos de guarda financeira — o prazo mais restritivo deve prevalecer.
        Student student = Student.builder()
                .id(11L)
                .fullName("Aluno Antigo")
                .updatedAt(LocalDateTime.now().minusYears(21))
                .documents(new ArrayList<>())
                .build();

        Payment recentPayment = Payment.builder()
                .id(500L)
                .dueDate(LocalDate.now().minusYears(1))
                .build();

        when(studentRepository.findByUserId(1L)).thenReturn(Optional.of(student));
        when(paymentRepository.findByStudentId(11L)).thenReturn(List.of(recentPayment));

        DataAnonymizationService.AnonymizationResult result = anonymizationService.anonymize(baseUser);

        assertFalse(result.completed());
        assertEquals(LocalDate.now().minusYears(1).plusYears(5), result.earliestEligibleDate());
    }

    @Test
    public void anonymizeCompletesAndScrubsPersonalDataWhenRetentionPeriodHasPassed() {
        StudentDocument doc = StudentDocument.builder()
                .id(1L)
                .filePath("uploads/students/11/documento.pdf")
                .build();
        List<StudentDocument> docs = new ArrayList<>(List.of(doc));

        Student student = Student.builder()
                .id(11L)
                .fullName("Aluno Elegível")
                .cpf("222.222.222-22")
                .phone("71999999999")
                .updatedAt(LocalDateTime.now().minusYears(25))
                .documents(docs)
                .fotoMatricula("uploads/students/11/foto.jpg")
                .build();

        when(studentRepository.findByUserId(1L)).thenReturn(Optional.of(student));
        when(paymentRepository.findByStudentId(11L)).thenReturn(List.of());

        DataAnonymizationService.AnonymizationResult result = anonymizationService.anonymize(baseUser);

        assertTrue(result.completed());
        assertNull(result.reason());

        // Documentos e foto removidos do storage e desvinculados do registro.
        verify(storageService, times(1)).delete("uploads/students/11/documento.pdf");
        verify(storageService, times(1)).delete("uploads/students/11/foto.jpg");
        assertTrue(student.getDocuments().isEmpty());
        assertNull(student.getFotoMatricula());

        // PII sobrescrita, registro preservado (soft) para integridade de histórico.
        assertEquals("000.000.000-00", student.getCpf());
        assertNull(student.getPhone());
        assertFalse(student.isActive());
        verify(studentRepository, times(1)).save(student);

        assertTrue(baseUser.getUsername().startsWith("usuario-anonimizado-"));
        verify(userRepository, times(1)).save(baseUser);
    }
}
