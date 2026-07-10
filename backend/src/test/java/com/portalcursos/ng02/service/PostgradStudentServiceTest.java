package com.portalcursos.ng02.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.portalcursos.ng02.model.Course;
import com.portalcursos.ng02.model.PostgradStudent;
import com.portalcursos.ng02.repository.PostgradStudentRepository;
import com.portalcursos.ng02.repository.StudentDocumentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.util.Optional;
import java.util.UUID;

@ExtendWith(MockitoExtension.class)
public class PostgradStudentServiceTest {

    @Mock
    private PostgradStudentRepository studentRepository;

    @Mock
    private StudentDocumentRepository documentRepository;

    @Mock
    private StorageService storageService;

    @InjectMocks
    private PostgradStudentService postgradStudentService;

    private PostgradStudent existingStudent;

    @BeforeEach
    public void setUp() {
        Course oldCourse = Course.builder().id(UUID.randomUUID()).denominacaoCurso("MBA Antigo").build();
        existingStudent = PostgradStudent.builder()
                .id(1L)
                .fullName("Maria Souza")
                .desiredCourse("MBA Antigo")
                .course(oldCourse)
                .enrollmentStatus("ATIVO")
                .build();
    }

    @Test
    public void updatePersisteMudancaDeCursoNaEntidadeGerenciada() throws IOException {
        Course newCourse = Course.builder().id(UUID.randomUUID()).denominacaoCurso("MBA Novo").build();
        PostgradStudent updatedData = PostgradStudent.builder()
                .fullName("Maria Souza")
                .desiredCourse("MBA Novo")
                .course(newCourse)
                .enrollmentStatus("ATIVO")
                .build();

        when(studentRepository.findById(1L)).thenReturn(Optional.of(existingStudent));
        when(studentRepository.save(any(PostgradStudent.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PostgradStudent result = postgradStudentService.update(1L, updatedData, null);

        assertEquals("MBA Novo", result.getDesiredCourse());
        assertEquals(newCourse.getId(), result.getCourse().getId(),
                "A FK course_id deve acompanhar a mudança de desiredCourse, não só o texto");
    }
}
