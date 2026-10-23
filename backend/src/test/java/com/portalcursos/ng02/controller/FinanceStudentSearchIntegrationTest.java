package com.portalcursos.ng02.controller;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.portalcursos.ng02.model.PostgradStudent;
import com.portalcursos.ng02.model.Student;
import com.portalcursos.ng02.repository.PostgradStudentRepository;
import com.portalcursos.ng02.repository.StudentRepository;
import com.portalcursos.ng02.service.UserDetailsImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Busca de aluno por nome ou CPF no formulário de cobrança, com H2 e repositórios reais (CPFs sintéticos). */
@SpringBootTest(properties = {
    "SPRING_DATASOURCE_URL=jdbc:h2:mem:financestudentsearchtestdb;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
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
@Transactional
class FinanceStudentSearchIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private StudentRepository studentRepository;
    @Autowired private PostgradStudentRepository postgradRepository;

    @BeforeEach
    void seed() {
        studentRepository.saveAndFlush(Student.builder().registrationNumber("G-SRCH-1").fullName("Maria Silva Souza")
                .email("maria.search@example.com").cpf("390.128.746-00").build());
        studentRepository.saveAndFlush(Student.builder().registrationNumber("G-SRCH-2").fullName("Mariana Costa")
                .email("mariana.search@example.com").cpf("11144477735").build());
        PostgradStudent post = PostgradStudent.builder().registrationNumber("P-SRCH-1").fullName("Marcos Pós Teste")
                .email("marcos.search@example.com").cpf("52998224725").build();
        post.setGraduationInstitution("Instituto Teste");
        post.setDesiredCourse("Curso Teste");
        postgradRepository.saveAndFlush(post);
    }

    private static RequestPostProcessor as(String role) {
        return user(new UserDetailsImpl(501L, "u-" + role, "u@x.com", "x", List.of(new SimpleGrantedAuthority("ROLE_" + role))));
    }

    private org.springframework.test.web.servlet.ResultActions search(String q, String level, String role) throws Exception {
        return mockMvc.perform(get("/api/finance/students/search").param("q", q).param("level", level).with(as(role)));
    }

    @Test
    void buscaPorParteDoNomeSemDiferenciarMaiusculas() throws Exception {
        search("MARI", "GRADUATION", "FINANCEIRO")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].fullName").value("Maria Silva Souza"));
    }

    @Test
    void buscaPorCpfComOuSemPontuacaoEMascaraOCpfNaResposta() throws Exception {
        search("390.128", "GRADUATION", "SECRETARIA")
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].cpfMasked").value("***.***.746-00"))
                .andExpect(content().string(not(containsString("39012874600"))));
        search("39012874600", "GRADUATION", "ADMIN").andExpect(jsonPath("$", hasSize(1)));
    }

    @Test
    void naoMisturaGraduacaoComPosGraduacao() throws Exception {
        search("Marcos", "GRADUATION", "ADMIN").andExpect(jsonPath("$", hasSize(0)));
        search("Marcos", "POSTGRADUATE", "ADMIN").andExpect(jsonPath("$", hasSize(1)));
        search("Maria", "POSTGRADUATE", "ADMIN").andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void textoCurtoDemaisEhRecusado() throws Exception {
        search("ma", "GRADUATION", "ADMIN").andExpect(status().isBadRequest());
        search("39", "GRADUATION", "ADMIN").andExpect(status().isBadRequest());
    }

    @Test
    void curingasDoLikeSaoTratadosComoTextoLiteral() throws Exception {
        search("%%%", "GRADUATION", "ADMIN").andExpect(status().isBadRequest());
        search("ma_ia", "GRADUATION", "ADMIN").andExpect(jsonPath("$", hasSize(0)));
    }

    private Long idOf(String registration) {
        return studentRepository.findByRegistrationNumber(registration).orElseThrow().getId();
    }

    private org.springframework.test.web.servlet.ResultActions summary(Long id, String level, String role) throws Exception {
        return mockMvc.perform(get("/api/finance/students/" + id + "/summary").param("level", level).with(as(role)));
    }

    @Test
    void financeiroVeSoNomeCpfMascaradoEMatriculaDoAluno() throws Exception {
        summary(idOf("G-SRCH-1"), "GRADUATION", "FINANCEIRO")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Maria Silva Souza"))
                .andExpect(jsonPath("$.cpfMasked").value("***.***.746-00"))
                .andExpect(jsonPath("$.registrationNumber").value("G-SRCH-1"))
                .andExpect(jsonPath("$.email").doesNotExist())
                .andExpect(content().string(not(containsString("39012874600"))));
    }

    @Test
    void resumoNaoMisturaNiveisNemEncontraIdInexistente() throws Exception {
        summary(idOf("P-SRCH-1"), "GRADUATION", "ADMIN").andExpect(status().isNotFound());
        summary(idOf("G-SRCH-1"), "POSTGRADUATE", "ADMIN").andExpect(status().isNotFound());
        summary(idOf("P-SRCH-1"), "POSTGRADUATE", "ADMIN").andExpect(status().isOk());
        summary(999999L, "GRADUATION", "ADMIN").andExpect(status().isNotFound());
    }

    @Test
    void resumoSoParaQuemLancaCobranca() throws Exception {
        Long id = idOf("G-SRCH-1");
        summary(id, "GRADUATION", "ALUNO").andExpect(status().isForbidden());
        summary(id, "GRADUATION", "ACADEMICO").andExpect(status().isForbidden());
        mockMvc.perform(get("/api/finance/students/" + id + "/summary").param("level", "GRADUATION"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void soQuemLancaCobrancaPodeBuscar() throws Exception {
        search("Maria", "GRADUATION", "ALUNO").andExpect(status().isForbidden());
        search("Maria", "GRADUATION", "ACADEMICO").andExpect(status().isForbidden());
        mockMvc.perform(get("/api/finance/students/search").param("q", "Maria").param("level", "GRADUATION"))
                .andExpect(status().isUnauthorized());
    }
}
