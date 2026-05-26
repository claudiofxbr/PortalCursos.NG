package com.portalcursos.ng02.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.portalcursos.ng02.model.Student;
import com.portalcursos.ng02.repository.StudentRepository;
import com.portalcursos.ng02.repository.StudentDocumentRepository;
import com.portalcursos.ng02.repository.PaymentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.io.IOException;
import java.util.ArrayList;

@ExtendWith(MockitoExtension.class)
public class StudentServiceTest {

    @Mock
    private StudentRepository studentRepository;

    @Mock
    private StudentDocumentRepository documentRepository;

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private StorageService storageService;

    @InjectMocks
    private StudentService studentService;

    private Student baseStudent;

    @BeforeEach
    public void setUp() {
        baseStudent = Student.builder()
                .id(1L)
                .fullName("João Silva")
                .email("joao.silva@example.com")
                .cpf("123.456.789-00")
                .phone("(71) 99999-8888")
                .address("Rua A, 10")
                .enrollmentStatus("PENDENTE")
                .documents(new ArrayList<>())
                .build();
    }

    @Test
    public void testSanitizationOnEnroll() throws IOException {
        // Mock do save
        when(studentRepository.save(any(Student.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Executar
        Student enrolled = studentService.enroll(baseStudent, null, new ArrayList<>());

        // Verificar sanitização do CPF
        assertEquals("12345678900", enrolled.getCpf(), "CPF deve ser salvo contendo apenas números");

        // Verificar sanitização do Telefone
        assertEquals("71999998888", enrolled.getPhone(), "Telefone deve ser salvo contendo apenas números");

        // Verificar atribuição de matrícula
        assertNotNull(enrolled.getRegistrationNumber(), "Matrícula deve ser gerada automaticamente");
        assertTrue(enrolled.getRegistrationNumber().startsWith("GRAD-"), "Prefixo da matrícula de graduação deve ser GRAD-");

        // Verificar status inicial
        assertEquals("PENDENTE_VALIDACAO", enrolled.getEnrollmentStatus(), "Status inicial de matrícula deve ser PENDENTE_VALIDACAO");

        // Verificar ativo
        assertTrue(enrolled.isActive(), "Estudante deve ser salvo ativo");

        verify(studentRepository, times(2)).save(any(Student.class));
    }

    @Test
    public void testExistsByEmailGlobal() {
        when(studentRepository.existsByEmailGlobal("joao.silva@example.com")).thenReturn(true);

        boolean exists = studentService.existsByEmailGlobal("joao.silva@example.com");

        assertTrue(exists);
        verify(studentRepository, times(1)).existsByEmailGlobal("joao.silva@example.com");
    }

    @Test
    public void testExistsByCpfGlobal() {
        when(studentRepository.existsByCpfGlobal("12345678900")).thenReturn(true);

        boolean exists = studentService.existsByCpfGlobal("12345678900");

        assertTrue(exists);
        verify(studentRepository, times(1)).existsByCpfGlobal("12345678900");
    }
}
