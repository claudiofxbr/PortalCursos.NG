package com.portalcursos.ng02.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.portalcursos.ng02.model.ChargePrice;
import com.portalcursos.ng02.model.Student;
import com.portalcursos.ng02.repository.ChargeAmountExceptionRepository;
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
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/**
 * Ativação da regra "valor sempre da tabela" de ponta a ponta, com a configuração LIGADA (o padrão de produção):
 * enquanto algum valor global estiver em branco a regra fica em espera (cobranças manuais continuam funcionando);
 * ao preencher o último valor ela passa a valer sozinha.
 */
@SpringBootTest(properties = {
    "SPRING_DATASOURCE_URL=jdbc:h2:mem:chargeruleactivationtestdb;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
    "SPRING_DATASOURCE_USERNAME=sa",
    "SPRING_DATASOURCE_PASSWORD=",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.flyway.enabled=false",
    "portalcursos.charges.enforce-table-price=true",
    "APP_JWT_SECRET=ZXhhbXBsZS1zZWNyZXQta2V5LXdpdGgtZW5vdWdoLWxlbmd0aC1mb3ItYmFzZTY0LWVuY29kaW5nLXByb3Blcmx5",
    "APP_JWT_EXPIRATION=900000",
    "APP_ROOT_PASSWORD=TestRootPass123!",
    "APP_ADMIN_PASSWORD=TestAdminPass123!"
})
@AutoConfigureMockMvc
@Transactional
class ChargeRuleActivationIntegrationTest {

    private static final String JSON = MediaType.APPLICATION_JSON_VALUE;

    @Autowired private MockMvc mockMvc;
    @Autowired private ChargePriceRepository priceRepository;
    @Autowired private ChargePriceChangeRepository changeRepository;
    @Autowired private ChargeAmountExceptionRepository exceptionRepository;
    @Autowired private StaffCourseScopeRepository scopeRepository;
    @Autowired private StudentRepository studentRepository;

    private Long studentId;
    private Long rematriculaId;
    private Long nadaConstaId;

    @BeforeEach
    void seed() {
        exceptionRepository.deleteAll();
        changeRepository.deleteAll();
        priceRepository.deleteAll();
        scopeRepository.deleteAll();
        rematriculaId = blankPrice("REMATRICULA", "Rematrícula periódica").getId();
        nadaConstaId = blankPrice("NADA_CONSTA", "Certidão de nada consta").getId();
        Student student = Student.builder().registrationNumber("GRAD-REGRA-1").fullName("Aluna Regra")
                .email("regra@example.com").cpf("39012874600").build();
        studentId = studentRepository.saveAndFlush(student).getId();
    }

    private ChargePrice blankPrice(String code, String name) {
        ChargePrice p = new ChargePrice();
        p.setCode(code);
        p.setCategory("Categoria");
        p.setName(name);
        return priceRepository.saveAndFlush(p);
    }

    private static RequestPostProcessor admin() {
        return user(new UserDetailsImpl(1L, "admin", "admin@x.com", "x", List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
    }

    private void fill(Long priceId, String value) throws Exception {
        Long version = priceRepository.findById(priceId).orElseThrow().getVersion();
        mockMvc.perform(put("/api/finance/prices/" + priceId).with(admin()).contentType(JSON)
                        .content("{\"value\":" + value + ",\"version\":" + version + "}"))
                .andExpect(status().isOk());
    }

    private String manualCharge() {
        return "{\"studentId\":" + studentId + ",\"academicLevel\":\"GRADUATION\",\"category\":\"TUITION\","
                + "\"amount\":250.00,\"dueDate\":\"2026-12-01\",\"description\":\"manual\"}";
    }

    private String chargeByProcedure(Long priceId) {
        return "{\"studentId\":" + studentId + ",\"academicLevel\":\"GRADUATION\",\"chargePriceId\":" + priceId
                + ",\"dueDate\":\"2026-12-01\"}";
    }

    @Test
    void comATabelaEmBrancoARegraFicaEmEsperaEACobrancaManualContinuaFuncionando() throws Exception {
        mockMvc.perform(get("/api/finance/charge-policy").with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enforceTablePrice").value(false))
                .andExpect(jsonPath("$.tableReady").value(false))
                .andExpect(jsonPath("$.blankValues").value(2));

        mockMvc.perform(post("/api/finance/charge").with(admin()).contentType(JSON).content(manualCharge()))
                .andExpect(status().isOk());
    }

    @Test
    void faltandoUmValorAindaEmEspera() throws Exception {
        fill(rematriculaId, "150.00");

        mockMvc.perform(get("/api/finance/charge-policy").with(admin()))
                .andExpect(jsonPath("$.enforceTablePrice").value(false))
                .andExpect(jsonPath("$.blankValues").value(1));
        mockMvc.perform(post("/api/finance/charge").with(admin()).contentType(JSON).content(manualCharge()))
                .andExpect(status().isOk());
    }

    @Test
    void aoPreencherOUltimoValorARegraPassaAValerSozinha() throws Exception {
        fill(rematriculaId, "150.00");
        fill(nadaConstaId, "15.50");

        mockMvc.perform(get("/api/finance/charge-policy").with(admin()))
                .andExpect(jsonPath("$.enforceTablePrice").value(true))
                .andExpect(jsonPath("$.tableReady").value(true))
                .andExpect(jsonPath("$.blankValues").value(0));

        // Valor digitado, sem procedimento: recusado.
        mockMvc.perform(post("/api/finance/charge").with(admin()).contentType(JSON).content(manualCharge()))
                .andExpect(status().isBadRequest());
        // Por procedimento: o valor vem da tabela, e o enviado é ignorado.
        mockMvc.perform(post("/api/finance/charge").with(admin()).contentType(JSON)
                        .content(chargeByProcedure(nadaConstaId).replace("}", ",\"amount\":9999.99}")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.amount").value(15.5));
    }

    @Test
    void oMomentoEmQueARegraEntraEmVigorFicaRegistradoNoLog() throws Exception {
        ch.qos.logback.classic.Logger logger =
                (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(com.portalcursos.ng02.service.ChargePriceService.class);
        ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent> appender = new ch.qos.logback.core.read.ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            fill(rematriculaId, "150.00");
            boolean antes = appender.list.stream().anyMatch(e -> e.getFormattedMessage().contains("ENTROU EM VIGOR"));
            fill(nadaConstaId, "15.50");
            boolean depois = appender.list.stream().anyMatch(e -> e.getFormattedMessage().contains("ENTROU EM VIGOR"));

            org.junit.jupiter.api.Assertions.assertFalse(antes, "faltava um valor: a regra ainda não entrou em vigor");
            org.junit.jupiter.api.Assertions.assertTrue(depois, "ao preencher o último valor, o log registra a entrada em vigor");
        } finally {
            logger.detachAppender(appender);
        }
    }

    @Test
    void comARegraValendoEditarOValorDaFaturaEhRecusado() throws Exception {
        fill(rematriculaId, "150.00");
        fill(nadaConstaId, "15.50");
        String created = mockMvc.perform(post("/api/finance/charge").with(admin()).contentType(JSON)
                        .content(chargeByProcedure(nadaConstaId)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("\"id\":(\\d+)").matcher(created);
        m.find();
        String invoiceId = m.group(1);

        mockMvc.perform(put("/api/finance/invoices/" + invoiceId).with(admin()).contentType(JSON)
                        .content("{\"amount\":1.00,\"dueDate\":\"2026-12-01\",\"category\":\"INTERNAL_PROCESS\","
                                + "\"secretaryProcessType\":\"CERTIFICATE\",\"description\":\"baixar o valor\"}"))
                .andExpect(status().isBadRequest());
    }
}
