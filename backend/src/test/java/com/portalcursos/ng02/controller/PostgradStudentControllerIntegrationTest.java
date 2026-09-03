package com.portalcursos.ng02.controller;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.portalcursos.ng02.model.Course;
import com.portalcursos.ng02.model.PostgradStudent;
import com.portalcursos.ng02.repository.CourseRepository;
import com.portalcursos.ng02.repository.PostgradStudentRepository;
import com.portalcursos.ng02.repository.StaffMemberRepository;
import com.portalcursos.ng02.repository.StudentDocumentRepository;
import com.portalcursos.ng02.service.StorageService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;

@SpringBootTest(properties = {
    "SPRING_DATASOURCE_URL=jdbc:h2:mem:postgradtestdb;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
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
public class PostgradStudentControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PostgradStudentRepository postgradStudentRepository;

    @MockitoBean
    private StudentDocumentRepository studentDocumentRepository;

    @MockitoBean
    private StorageService storageService;

    @MockitoBean
    private CourseRepository courseRepository;

    @MockitoBean
    private StaffMemberRepository staffMemberRepository;

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    public void testListAllSuccess() throws Exception {
        PostgradStudent student = PostgradStudent.builder()
                .id(1L)
                .fullName("Aluno Pós")
                .email("pos@example.com")
                .cpf("12345678900")
                .registrationNumber("POS-TESTE01")
                .graduationInstitution("UFBA")
                .desiredCourse("MBA")
                .build();
        when(postgradStudentRepository.findAllActive()).thenReturn(Arrays.asList(student));

        mockMvc.perform(get("/api/v1/postgrad-students").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].fullName").value("Aluno Pós"));
    }

    @Test
    @WithMockUser(username = "aluno", roles = {"ALUNO"})
    public void testListAllAccessDeniedForAluno() throws Exception {
        mockMvc.perform(get("/api/v1/postgrad-students").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    public void testFindByIdNotFoundReturns404Empty() throws Exception {
        when(postgradStudentRepository.findByIdAndActiveTrue(999L)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/postgrad-students/999").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(username = "secretaria", roles = {"SECRETARIA"})
    public void testCreateWithDuplicateEmailReturns400() throws Exception {
        when(postgradStudentRepository.existsByEmailGlobal("duplicado@example.com")).thenReturn(true);

        mockMvc.perform(multipart("/api/v1/postgrad-students")
                .param("fullName", "Aluno Duplicado")
                .param("email", "duplicado@example.com")
                .param("cpf", "11122233344")
                .param("phone", "71999999999")
                .param("graduationInstitution", "UFBA")
                .param("desiredCourse", "MBA"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Email já cadastrado em nossa base de dados."));
    }

    // Regressão: falha de storage durante criação não deve vazar detalhes internos
    // (path do servidor, causa raiz da IOException) na resposta ao cliente.
    @Test
    @WithMockUser(username = "matricula", roles = {"MATRICULA"})
    public void testCreateStorageFailureDoesNotLeakInternalDetails() throws Exception {
        when(postgradStudentRepository.existsByEmailGlobal("novo.pos@example.com")).thenReturn(false);
        when(postgradStudentRepository.existsByCpfGlobal("55566677788")).thenReturn(false);

        Course course = Course.builder().id(UUID.randomUUID()).codigoIes("IES01").denominacaoCurso("MBA Gestão").build();
        when(courseRepository.findByDenominacaoCurso("MBA Gestão")).thenReturn(Optional.of(course));
        when(postgradStudentRepository.saveAndFlush(org.mockito.ArgumentMatchers.any(PostgradStudent.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        when(storageService.store(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq("postgrad/diplomas")))
                .thenThrow(new java.io.IOException(
                        "Falha ao escrever em C:\\Users\\VeKTI-01\\Desktop\\uploads\\postgrad\\diplomas\\segredo.pdf: Disco cheio"));

        MockMultipartFile diploma = new MockMultipartFile(
                "diplomaFile", "diploma.pdf", "application/pdf", "conteudo-fake".getBytes());

        mockMvc.perform(multipart("/api/v1/postgrad-students")
                .file(diploma)
                .param("fullName", "Novo Pós")
                .param("email", "novo.pos@example.com")
                .param("cpf", "55566677788")
                .param("phone", "71988887777")
                .param("graduationInstitution", "UFBA")
                .param("desiredCourse", "MBA Gestão"))
                .andExpect(status().isInternalServerError())
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("C:\\Users"))))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("Disco cheio"))));
    }
}
