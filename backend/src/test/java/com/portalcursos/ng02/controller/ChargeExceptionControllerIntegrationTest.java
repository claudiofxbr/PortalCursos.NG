package com.portalcursos.ng02.controller;

import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.portalcursos.ng02.model.ChargePrice;
import com.portalcursos.ng02.model.Course;
import com.portalcursos.ng02.model.Payment;
import com.portalcursos.ng02.model.StaffCourseScope;
import com.portalcursos.ng02.model.Student;
import com.portalcursos.ng02.repository.ChargeAmountExceptionRepository;
import com.portalcursos.ng02.repository.ChargePriceChangeRepository;
import com.portalcursos.ng02.repository.ChargePriceRepository;
import com.portalcursos.ng02.repository.CourseRepository;
import com.portalcursos.ng02.repository.PaymentRepository;
import com.portalcursos.ng02.repository.StaffCourseScopeRepository;
import com.portalcursos.ng02.repository.StudentRepository;
import com.portalcursos.ng02.service.UserDetailsImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** Fluxo completo do pedido de exceção, com perfis e escopo por curso reais (H2 + repositórios reais). */
@SpringBootTest(properties = {
    "SPRING_DATASOURCE_URL=jdbc:h2:mem:chargeexceptiontestdb;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
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
class ChargeExceptionControllerIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ChargeAmountExceptionRepository exceptionRepository;
    @Autowired private ChargePriceRepository priceRepository;
    @Autowired private ChargePriceChangeRepository changeRepository;
    @Autowired private StaffCourseScopeRepository scopeRepository;
    @Autowired private StudentRepository studentRepository;
    @Autowired private CourseRepository courseRepository;
    @Autowired private PaymentRepository paymentRepository;

    private Long studentId;
    private Long priceId;

    @BeforeEach
    void seed() {
        exceptionRepository.deleteAll();
        changeRepository.deleteAll();
        priceRepository.deleteAll();
        scopeRepository.deleteAll();

        Course course = new Course();
        course.setCodigoIes("T1");
        course.setDenominacaoCurso("Curso Exceção");
        course = courseRepository.saveAndFlush(course);

        ChargePrice price = new ChargePrice();
        price.setCode("NADA_CONSTA");
        price.setCategory("Conclusão e Expedição de Documentos");
        price.setName("Certidão de nada consta");
        price.setCurrentValue(new BigDecimal("100.00"));
        priceId = priceRepository.saveAndFlush(price).getId();

        Student student = Student.builder().registrationNumber("GRAD-EXC-1").fullName("Aluna Exceção")
                .email("excecao@example.com").cpf("39012874600").course(course).build();
        studentId = studentRepository.saveAndFlush(student).getId();

        StaffCourseScope scope = new StaffCourseScope();
        scope.setStaffId(100L);
        scope.setCourseId(course.getId());
        scope.setHead(false);
        scopeRepository.saveAndFlush(scope);
        // Secretária 101 atende só outro curso.
        StaffCourseScope other = new StaffCourseScope();
        other.setStaffId(101L);
        other.setCourseId(UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"));
        other.setHead(false);
        scopeRepository.saveAndFlush(other);
    }

    private static RequestPostProcessor as(long id, String name, String role) {
        return user(new UserDetailsImpl(id, name, name + "@x.com", "x", List.of(new SimpleGrantedAuthority("ROLE_" + role))));
    }

    private String body(String amount, String reason) {
        return "{\"studentId\":" + studentId + ",\"academicLevel\":\"GRADUATION\",\"chargePriceId\":" + priceId
                + ",\"dueDate\":\"2026-12-01\",\"requestedAmount\":" + amount + ",\"reason\":\"" + reason + "\"}";
    }

    private static long idOf(String json, String field) {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("\"" + field + "\":(\\d+)").matcher(json);
        if (!m.find()) {
            throw new IllegalStateException("campo " + field + " ausente em " + json);
        }
        return Long.parseLong(m.group(1));
    }

    private long requestAs(RequestPostProcessor who) throws Exception {
        String json = mockMvc.perform(post("/api/finance/charge-exceptions").with(who)
                        .contentType(MediaType.APPLICATION_JSON).content(body("60.00", "Bolsa parcial concedida pela direção")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return idOf(json, "id");
    }

    @Test
    void secretariaDoCursoDoAlunoPedeEFicaPendenteSemCobranca() throws Exception {
        mockMvc.perform(post("/api/finance/charge-exceptions").with(as(100, "ana", "SECRETARIA"))
                        .contentType(MediaType.APPLICATION_JSON).content(body("60.00", "Bolsa parcial concedida pela direção")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.tableAmount").value(100.0))
                .andExpect(jsonPath("$.requestedAmount").value(60.0))
                .andExpect(jsonPath("$.requestedBy").value("ana"));
        assertEquals(0, paymentRepository.count());
    }

    @Test
    void secretariaDeOutroCursoRecebe403() throws Exception {
        mockMvc.perform(post("/api/finance/charge-exceptions").with(as(101, "bia", "SECRETARIA"))
                        .contentType(MediaType.APPLICATION_JSON).content(body("60.00", "Bolsa parcial concedida pela direção")))
                .andExpect(status().isForbidden());
    }

    @Test
    void secretariaSemNenhumCursoVinculadoRecebe403() throws Exception {
        mockMvc.perform(post("/api/finance/charge-exceptions").with(as(999, "sem-escopo", "SECRETARIA"))
                        .contentType(MediaType.APPLICATION_JSON).content(body("60.00", "Bolsa parcial concedida pela direção")))
                .andExpect(status().isForbidden());
    }

    @Test
    void financeiroEAlunoNaoPedemNemListam() throws Exception {
        for (String role : List.of("FINANCEIRO", "ALUNO")) {
            mockMvc.perform(post("/api/finance/charge-exceptions").with(as(300, "u-" + role, role))
                            .contentType(MediaType.APPLICATION_JSON).content(body("60.00", "Bolsa parcial concedida pela direção")))
                    .andExpect(status().isForbidden());
            mockMvc.perform(get("/api/finance/charge-exceptions").with(as(300, "u-" + role, role)))
                    .andExpect(status().isForbidden());
        }
    }

    @Test
    void semAutenticacaoRetorna401() throws Exception {
        mockMvc.perform(get("/api/finance/charge-exceptions")).andExpect(status().isUnauthorized());
    }

    @Test
    void secretariaNaoAprovaNemRejeita() throws Exception {
        long id = requestAs(as(100, "ana", "SECRETARIA"));
        mockMvc.perform(post("/api/finance/charge-exceptions/" + id + "/approve").with(as(100, "ana", "SECRETARIA")))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/finance/charge-exceptions/" + id + "/reject").with(as(100, "ana", "SECRETARIA"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"não\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void quemPediuNaoAprovaOProprioPedidoMasOutroAdministradorAprovaEACobrancaNasceComOValorAutorizado() throws Exception {
        long id = requestAs(as(1, "adm1", "ADMIN"));

        mockMvc.perform(post("/api/finance/charge-exceptions/" + id + "/approve").with(as(1, "adm1", "ADMIN")))
                .andExpect(status().isForbidden());
        assertEquals(0, paymentRepository.count());

        String json = mockMvc.perform(post("/api/finance/charge-exceptions/" + id + "/approve").with(as(2, "adm2", "ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Conferido\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.decidedBy").value("adm2"))
                .andReturn().getResponse().getContentAsString();

        Payment issued = paymentRepository.findById(idOf(json, "paymentId")).orElseThrow();
        assertEquals(0, issued.getAmount().compareTo(new BigDecimal("60.00")));
        assertEquals(priceId, issued.getChargePriceId());

        mockMvc.perform(post("/api/finance/charge-exceptions/" + id + "/approve").with(as(2, "adm2", "ADMIN")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejeicaoExigeMotivoENaoEmiteCobranca() throws Exception {
        long id = requestAs(as(100, "ana", "SECRETARIA"));

        mockMvc.perform(post("/api/finance/charge-exceptions/" + id + "/reject").with(as(2, "adm2", "ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"\"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/finance/charge-exceptions/" + id + "/reject").with(as(2, "adm2", "ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Fora da política de bolsas\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"));
        assertEquals(0, paymentRepository.count());
    }

    @Test
    void listagemMostraTudoAoAdministradorESoOsProprioPedidosASecretaria() throws Exception {
        requestAs(as(100, "ana", "SECRETARIA"));

        mockMvc.perform(get("/api/finance/charge-exceptions").with(as(2, "adm2", "ADMIN")))
                .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].studentName").value("Aluna Exceção"));
        mockMvc.perform(get("/api/finance/charge-exceptions").with(as(100, "ana", "SECRETARIA")))
                .andExpect(jsonPath("$", hasSize(1)));
        mockMvc.perform(get("/api/finance/charge-exceptions").with(as(101, "bia", "SECRETARIA")))
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void politicaInformaARegraEOQueCadaPerfilPodeFazer() throws Exception {
        mockMvc.perform(get("/api/finance/charge-policy").with(as(2, "adm2", "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enforceTablePrice").value(false))
                .andExpect(jsonPath("$.canRequestException").value(true))
                .andExpect(jsonPath("$.canDecideException").value(true));
        mockMvc.perform(get("/api/finance/charge-policy").with(as(100, "ana", "SECRETARIA")))
                .andExpect(jsonPath("$.canRequestException").value(true))
                .andExpect(jsonPath("$.canDecideException").value(false));
        mockMvc.perform(get("/api/finance/charge-policy").with(as(300, "fin", "FINANCEIRO")))
                .andExpect(jsonPath("$.canRequestException").value(false))
                .andExpect(jsonPath("$.canDecideException").value(false));
    }
}
