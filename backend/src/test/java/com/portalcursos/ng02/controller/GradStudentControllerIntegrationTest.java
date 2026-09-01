package com.portalcursos.ng02.controller;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.portalcursos.ng02.model.Course;
import com.portalcursos.ng02.model.Student;
import com.portalcursos.ng02.repository.CourseRepository;
import com.portalcursos.ng02.repository.PaymentRepository;
import com.portalcursos.ng02.repository.StaffMemberRepository;
import com.portalcursos.ng02.repository.StudentDocumentRepository;
import com.portalcursos.ng02.repository.StudentRepository;
import com.portalcursos.ng02.service.StorageService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;

@SpringBootTest(properties = {
    "SPRING_DATASOURCE_URL=jdbc:h2:mem:gradstudenttestdb;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
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
public class GradStudentControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private StudentRepository studentRepository;

    @MockBean
    private StudentDocumentRepository studentDocumentRepository;

    @MockBean
    private PaymentRepository paymentRepository;

    @MockBean
    private StorageService storageService;

    @MockBean
    private CourseRepository courseRepository;

    @MockBean
    private StaffMemberRepository staffMemberRepository;

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    public void testGetAllGradStudentsSuccess() throws Exception {
        Student student = Student.builder()
                .id(1L)
                .fullName("Aluno Graduação")
                .email("aluno@example.com")
                .cpf("12345678900")
                .registrationNumber("GRAD-TESTE01")
                .build();
        when(studentRepository.findAllWithCourseAndCreator()).thenReturn(Arrays.asList(student));

        mockMvc.perform(get("/api/v1/grad-students").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].fullName").value("Aluno Graduação"));
    }

    @Test
    @WithMockUser(username = "aluno", roles = {"ALUNO"})
    public void testGetAllGradStudentsAccessDeniedForAluno() throws Exception {
        mockMvc.perform(get("/api/v1/grad-students").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "secretaria", roles = {"SECRETARIA"})
    public void testUpdateStudentCourseNotFoundReturns404() throws Exception {
        when(courseRepository.findByDenominacaoCurso("Curso Inexistente")).thenReturn(Optional.empty());

        mockMvc.perform(multipart(org.springframework.http.HttpMethod.PUT, "/api/v1/grad-students/1")
                .param("fullName", "Aluno Teste")
                .param("phone", "71999999999")
                .param("address", "Rua Teste, 123")
                .param("currentCourse", "Curso Inexistente")
                .param("enrollmentStatus", "ATIVO"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("Curso não encontrado: Curso Inexistente"));
    }

    // Regressão: falha de storage durante matrícula não deve vazar detalhes internos
    // (path do servidor, causa raiz) na resposta ao cliente.
    @Test
    @WithMockUser(username = "matricula", roles = {"MATRICULA"})
    public void testEnrollStudentStorageFailureDoesNotLeakInternalDetails() throws Exception {
        when(studentRepository.existsByEmailGlobal("novo.aluno@example.com")).thenReturn(false);
        when(studentRepository.existsByCpfGlobal("98765432100")).thenReturn(false);

        Course course = Course.builder().id(UUID.randomUUID()).codigoIes("IES01").denominacaoCurso("Engenharia").build();
        when(courseRepository.findByDenominacaoCurso("Engenharia")).thenReturn(Optional.of(course));
        when(studentRepository.save(org.mockito.ArgumentMatchers.any(Student.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        when(storageService.store(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq("fotos-perfil")))
                .thenThrow(new java.io.IOException(
                        "Falha ao escrever em C:\\Users\\VeKTI-01\\Desktop\\uploads\\fotos-perfil\\segredo.png: Disco cheio"));

        MockMultipartFile foto = new MockMultipartFile(
                "foto3x4", "foto.png", "image/png", "conteudo-fake".getBytes());

        mockMvc.perform(multipart("/api/v1/grad-students/enroll")
                .file(foto)
                .param("fullName", "Novo Aluno")
                .param("email", "novo.aluno@example.com")
                .param("cpf", "98765432100")
                .param("phone", "71988887777")
                .param("dateOfBirth", "2000-01-01")
                .param("address", "Rua Nova, 10")
                .param("currentCourse", "Engenharia")
                .param("nacionalidade", "Brasileira")
                .param("estadoCivil", "SOLTEIRO")
                .param("sexo", "M")
                .param("isEstrangeiro", "false")
                .param("formaIngresso", "ENEM_SISU")
                .param("tipoCota", "NENHUMA"))
                .andExpect(status().isInternalServerError())
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("C:\\Users"))))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("Disco cheio"))));
    }
}
