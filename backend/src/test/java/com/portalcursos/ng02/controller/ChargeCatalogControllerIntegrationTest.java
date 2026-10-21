package com.portalcursos.ng02.controller;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.portalcursos.ng02.model.ChargePrice;
import com.portalcursos.ng02.model.Student;
import com.portalcursos.ng02.repository.ChargePriceChangeRepository;
import com.portalcursos.ng02.repository.ChargePriceRepository;
import com.portalcursos.ng02.repository.StaffCourseScopeRepository;
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

import java.math.BigDecimal;
import java.util.List;

/** Acesso e contrato da rota de catálogo de procedimentos, com banco H2 e repositórios reais. */
@SpringBootTest(properties = {
    "SPRING_DATASOURCE_URL=jdbc:h2:mem:chargecatalogtestdb;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
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
class ChargeCatalogControllerIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ChargePriceRepository priceRepository;
    @Autowired private ChargePriceChangeRepository changeRepository;
    @Autowired private StaffCourseScopeRepository scopeRepository;
    @Autowired private StudentRepository studentRepository;

    private Long studentId;

    @BeforeEach
    void seed() {
        changeRepository.deleteAll();
        priceRepository.deleteAll();
        scopeRepository.deleteAll();
        price("NADA_CONSTA", ChargePrice.Scope.ALL, "15.50");
        price("TUITION", ChargePrice.Scope.ALL, null);
        price("EXAME_QUALIFICACAO", ChargePrice.Scope.POSTGRAD, "20.00");
        Student student = Student.builder().registrationNumber("GRAD-CAT-1").fullName("Aluno Catálogo")
                .email("catalogo@example.com").cpf("39012874600").build();
        studentId = studentRepository.saveAndFlush(student).getId();
    }

    private void price(String code, ChargePrice.Scope scope, String value) {
        ChargePrice p = new ChargePrice();
        p.setCode(code);
        p.setCategory("Categoria");
        p.setName("Nome " + code);
        p.setAcademicScope(scope);
        p.setCurrentValue(value == null ? null : new BigDecimal(value));
        priceRepository.saveAndFlush(p);
    }

    private static RequestPostProcessor as(String role) {
        return user(new UserDetailsImpl(500L, "u-" + role, "u@x.com", "x", List.of(new SimpleGrantedAuthority("ROLE_" + role))));
    }

    @Test
    void financeiroConsegueConsultarOCatalogoDoAlunoDeGraduacao() throws Exception {
        mockMvc.perform(get("/api/finance/charge-catalog?studentId=" + studentId + "&level=GRADUATION").with(as("FINANCEIRO")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[?(@.code=='NADA_CONSTA')].value").value(15.5))
                .andExpect(jsonPath("$[?(@.code=='NADA_CONSTA')].valueDefined").value(true))
                .andExpect(jsonPath("$[?(@.code=='NADA_CONSTA')].processType").value("CERTIFICATE"))
                .andExpect(jsonPath("$[?(@.code=='TUITION')].valueDefined").value(false));
    }

    @Test
    void procedimentoDePosGraduacaoNaoApareceParaAlunoDeGraduacao() throws Exception {
        mockMvc.perform(get("/api/finance/charge-catalog?studentId=" + studentId + "&level=GRADUATION").with(as("ADMIN")))
                .andExpect(jsonPath("$[?(@.code=='EXAME_QUALIFICACAO')]").isEmpty());
    }

    @Test
    void alunoNaoPodeConsultarOCatalogo() throws Exception {
        mockMvc.perform(get("/api/finance/charge-catalog?studentId=" + studentId + "&level=GRADUATION").with(as("ALUNO")))
                .andExpect(status().isForbidden());
    }

    @Test
    void semAutenticacaoRetorna401() throws Exception {
        mockMvc.perform(get("/api/finance/charge-catalog?studentId=" + studentId + "&level=GRADUATION"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void nivelInvalidoViraErroDoClienteNuncaErro500() throws Exception {
        mockMvc.perform(get("/api/finance/charge-catalog?studentId=" + studentId + "&level=XYZ").with(as("ADMIN")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void alunoInexistenteRetorna404() throws Exception {
        mockMvc.perform(get("/api/finance/charge-catalog?studentId=999999&level=GRADUATION").with(as("ADMIN")))
                .andExpect(status().isNotFound());
    }
}
