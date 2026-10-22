package com.portalcursos.ng02.controller;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.portalcursos.ng02.model.Course;
import com.portalcursos.ng02.model.PostgradStudent;
import com.portalcursos.ng02.repository.CourseRepository;
import com.portalcursos.ng02.repository.PostgradStudentRepository;
import com.portalcursos.ng02.service.UserDetailsImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.List;

/**
 * Regressão: a lista e o detalhe de alunos de pós-graduação davam 500 (LazyInitializationException ao ler o
 * curso) assim que existia um aluno com curso, porque a produção roda com open-in-view desligado e as consultas
 * não carregavam o curso. Este teste NÃO pode ser {@code @Transactional}: a transação do teste manteria a sessão
 * aberta durante a requisição e esconderia exatamente esse defeito.
 */
@SpringBootTest(properties = {
    "SPRING_DATASOURCE_URL=jdbc:h2:mem:postgradcoursetestdb;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
    "SPRING_DATASOURCE_USERNAME=sa",
    "SPRING_DATASOURCE_PASSWORD=",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.jpa.open-in-view=false",
    "spring.flyway.enabled=false",
    "APP_JWT_SECRET=ZXhhbXBsZS1zZWNyZXQta2V5LXdpdGgtZW5vdWdoLWxlbmd0aC1mb3ItYmFzZTY0LWVuY29kaW5nLXByb3Blcmx5",
    "APP_JWT_EXPIRATION=900000",
    "APP_ROOT_PASSWORD=TestRootPass123!",
    "APP_ADMIN_PASSWORD=TestAdminPass123!"
})
@AutoConfigureMockMvc
class PostgradStudentCourseLoadingIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private PostgradStudentRepository postgradRepository;
    @Autowired private CourseRepository courseRepository;
    @Autowired private JdbcTemplate jdbcTemplate;

    private Long studentId;

    private static RequestPostProcessor admin() {
        return user(new UserDetailsImpl(1L, "admin-teste", "a@x.com", "x", List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
    }

    @BeforeEach
    void seed() {
        Course course = new Course();
        course.setCodigoIes("POS-1");
        course.setDenominacaoCurso("Mestrado em Testes");
        course = courseRepository.saveAndFlush(course);

        PostgradStudent student = PostgradStudent.builder().registrationNumber("POS-T-1")
                .fullName("Mestranda Curso Carregado").email("pos.curso@example.com").cpf("39012874600")
                .graduationInstitution("Instituição de Teste").desiredCourse("Mestrado em Testes")
                .course(course).build();
        studentId = postgradRepository.saveAndFlush(student).getId();
    }

    @AfterEach
    void cleanUp() {
        wipe();
    }

    /** SQL direto: deleteAll() do Spring Data em entidade com @Version + soft-delete falha neste projeto. */
    private void wipe() {
        jdbcTemplate.update("DELETE FROM students");
        jdbcTemplate.update("DELETE FROM courses");
    }

    @Test
    void listaDeAlunosDePosComCursoNaoDa500() throws Exception {
        mockMvc.perform(get("/api/v1/postgrad-students").with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].currentCourse").value("Mestrado em Testes"));
    }

    @Test
    void detalheDoAlunoDePosComCursoNaoDa500() throws Exception {
        mockMvc.perform(get("/api/v1/postgrad-students/" + studentId).with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentCourse").value("Mestrado em Testes"));
    }

    @Test
    void alunoDePosSemCursoContinuaListandoSemErro() throws Exception {
        jdbcTemplate.update("DELETE FROM students");
        PostgradStudent semCurso = PostgradStudent.builder().registrationNumber("POS-T-2")
                .fullName("Mestranda Sem Curso").email("pos.sem@example.com").cpf("11144477735")
                .graduationInstitution("Instituição de Teste").desiredCourse("Curso que não existe").build();
        postgradRepository.saveAndFlush(semCurso);

        mockMvc.perform(get("/api/v1/postgrad-students").with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].currentCourse").doesNotExist());
    }
}
