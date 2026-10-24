package com.portalcursos.ng02.controller;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.portalcursos.ng02.model.Student;
import com.portalcursos.ng02.repository.StaffMemberRepository;
import com.portalcursos.ng02.repository.StudentRepository;
import com.portalcursos.ng02.repository.TeacherRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Arrays;
import java.util.Collections;

@SpringBootTest(properties = {
    "SPRING_DATASOURCE_URL=jdbc:h2:mem:academictestdb;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
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
public class AcademicControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private StudentRepository studentRepository;

    @MockitoBean
    private TeacherRepository teacherRepository;

    @MockitoBean
    private StaffMemberRepository staffMemberRepository;

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    public void testGetAllStudentsSuccess() throws Exception {
        Student student = Student.builder()
                .id(1L)
                .fullName("Aluno Teste")
                .email("aluno@example.com")
                .cpf("12345678900")
                .registrationNumber("GRAD-TESTE01")
                .build();
        when(studentRepository.findAllWithCourseAndCreator()).thenReturn(Arrays.asList(student));

        mockMvc.perform(get("/api/academic/students").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].fullName").value("Aluno Teste"));

        verify(studentRepository, times(1)).findAllWithCourseAndCreator();
    }

    @Test
    @WithMockUser(username = "student", roles = {"ALUNO"})
    public void testGetAllStudentsAccessDeniedForAluno() throws Exception {
        mockMvc.perform(get("/api/academic/students").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "secretaria", roles = {"SECRETARIA"})
    public void testGetAllStaffAccessDeniedForSecretaria() throws Exception {
        // /staff só permite ADMIN e ROOT_MASTER, diferente de /students que também aceita SECRETARIA
        mockMvc.perform(get("/api/academic/staff").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());
    }

    // Regressão: falha de acesso ao banco não deve vazar detalhes internos
    // (host, credenciais) na resposta ao cliente — deve cair no handler padrão de DataAccessException.
    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    public void testGetAllUsersDatabaseErrorDoesNotLeakInternalDetails() throws Exception {
        when(studentRepository.findAllWithCourse())
                .thenThrow(new DataAccessResourceFailureException(
                        "Connection refused to internal-db-host:5432 user=admin password=SECRET123"));

        mockMvc.perform(get("/api/academic/users").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value(503))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("internal-db-host"))))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("SECRET123"))));
    }

    // M8 da auditoria: o papel do colaborador vem das roles reais, não de um rótulo fixo "SECRETARIA".
    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    public void testAcademicUsersUsaORoleRealDoColaborador() throws Exception {
        com.portalcursos.ng02.model.StaffMember coord = com.portalcursos.ng02.model.StaffMember.builder()
                .id(11L).fullName("Coord Teste").position("Coordenador").department("Cursos").build();
        com.portalcursos.ng02.model.StaffMember semRole = com.portalcursos.ng02.model.StaffMember.builder()
                .id(12L).fullName("Sem Role").position("Apoio").department("Geral").build();
        when(staffMemberRepository.findAllByActiveTrue()).thenReturn(Arrays.asList(coord, semRole));
        when(staffMemberRepository.findActiveStaffRoles()).thenReturn(Arrays.asList(
                new Object[] {11L, com.portalcursos.ng02.model.Role.ERole.ROLE_SECRETARIA},
                new Object[] {11L, com.portalcursos.ng02.model.Role.ERole.ROLE_COORDENADOR}));

        mockMvc.perform(get("/api/academic/users"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id=='M_11')].role").value("COORDENADOR"))
                .andExpect(jsonPath("$[?(@.id=='M_12')].role").value("COLABORADOR"));
    }

    // M9 da auditoria: paginação opt-in dos alunos em dois passos (ids paginados, depois fetch com documentos).
    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    public void testAlunosPaginadosPreservamAOrdemDosIdsETotal() throws Exception {
        Student a = Student.builder().id(20L).fullName("Aluno A").build();
        Student b = Student.builder().id(10L).fullName("Aluno B").build();
        when(studentRepository.findActiveStudentIds(any(org.springframework.data.domain.Pageable.class)))
                .thenReturn(new org.springframework.data.domain.PageImpl<>(
                        Arrays.asList(20L, 10L), org.springframework.data.domain.PageRequest.of(0, 2), 7));
        when(studentRepository.findAllWithCourseAndCreatorByIdIn(anyCollection())).thenReturn(Arrays.asList(b, a));

        mockMvc.perform(get("/api/academic/students").param("page", "0").param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Total-Count", "7"))
                .andExpect(jsonPath("$[0].id").value(20))
                .andExpect(jsonPath("$[1].id").value(10));

        verify(studentRepository, never()).findAllWithCourseAndCreator();
    }

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    public void testAlunosSemParametrosSeguemSemPaginacao() throws Exception {
        when(studentRepository.findAllWithCourseAndCreator()).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/api/academic/students"))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist("X-Total-Count"));

        verify(studentRepository, never()).findActiveStudentIds(any());
    }

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    public void testAlunosPaginacaoInvalidaRetorna400() throws Exception {
        mockMvc.perform(get("/api/academic/students").param("size", "500"))
                .andExpect(status().isBadRequest());
    }
}
