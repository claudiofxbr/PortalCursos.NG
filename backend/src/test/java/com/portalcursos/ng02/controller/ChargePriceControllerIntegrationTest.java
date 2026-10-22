package com.portalcursos.ng02.controller;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.portalcursos.ng02.model.ChargePrice;
import com.portalcursos.ng02.model.ChargePriceChange;
import com.portalcursos.ng02.model.StaffCourseScope;
import com.portalcursos.ng02.repository.ChargePriceChangeRepository;
import com.portalcursos.ng02.repository.ChargePriceRepository;
import com.portalcursos.ng02.repository.StaffCourseScopeRepository;
import com.portalcursos.ng02.service.UserDetailsImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** Matriz de acesso da tabela de valores, com banco H2 e repositórios reais. */
@SpringBootTest(properties = {
    "SPRING_DATASOURCE_URL=jdbc:h2:mem:chargepricestestdb;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
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
class ChargePriceControllerIntegrationTest {

    private static final UUID COURSE_A = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID COURSE_B = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");

    @Autowired private MockMvc mockMvc;
    @Autowired private ChargePriceRepository priceRepository;
    @Autowired private ChargePriceChangeRepository changeRepository;
    @Autowired private StaffCourseScopeRepository scopeRepository;

    private Long globalId;
    private Long courseAId;
    private Long courseBId;

    @BeforeEach
    void seed() {
        changeRepository.deleteAll();
        priceRepository.deleteAll();
        scopeRepository.deleteAll();
        globalId = price("REMATRICULA", null, "100.00").getId();
        courseAId = price("REMATRICULA", COURSE_A, "120.00").getId();
        courseBId = price("REMATRICULA", COURSE_B, "130.00").getId();
        scope(100L, COURSE_A, true);   // chefe do curso A
        scope(101L, COURSE_A, false);  // secretaria do curso A (leitura)
    }

    private ChargePrice price(String code, UUID course, String value) {
        ChargePrice p = new ChargePrice();
        p.setCode(code);
        p.setCategory("Renovação e Ajustes Curriculares");
        p.setName("Rematrícula periódica");
        p.setCourseId(course);
        p.setCurrentValue(new BigDecimal(value));
        return priceRepository.saveAndFlush(p);
    }

    private void scope(Long staffId, UUID course, boolean head) {
        StaffCourseScope s = new StaffCourseScope();
        s.setStaffId(staffId);
        s.setCourseId(course);
        s.setHead(head);
        scopeRepository.saveAndFlush(s);
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor secretaria(long id, String name) {
        return user(new UserDetailsImpl(id, name, name + "@x.com", "x", List.of(new SimpleGrantedAuthority("ROLE_SECRETARIA"))));
    }

    private static final String JSON = MediaType.APPLICATION_JSON_VALUE;

    // ------------------------------------------------------------------ visão por perfil

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void administradorVeTudoEPodeEditar() throws Exception {
        mockMvc.perform(get("/api/finance/prices"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("ADMIN"))
                .andExpect(jsonPath("$.rows", hasSize(3)))
                .andExpect(jsonPath("$.rows[0].canEdit").value(true));
    }

    @Test
    void chefeVeGlobalESeuCursoEPropoeSoNoSeuCurso() throws Exception {
        mockMvc.perform(get("/api/finance/prices").with(secretaria(100, "chefe")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("HEAD"))
                .andExpect(jsonPath("$.rows", hasSize(2)))
                .andExpect(jsonPath("$.rows[?(@.courseId=='" + COURSE_A + "')].canPropose").value(true))
                .andExpect(jsonPath("$.rows[?(@.courseId==null)].canPropose").value(false))
                .andExpect(jsonPath("$.rows[?(@.courseId=='" + COURSE_B + "')]").isEmpty())
                .andExpect(jsonPath("$.rows[0].canEdit").value(false));
    }

    @Test
    void secretariaSoLe() throws Exception {
        mockMvc.perform(get("/api/finance/prices").with(secretaria(101, "sec")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("SECRETARIA"))
                .andExpect(jsonPath("$.rows", hasSize(2)))
                .andExpect(jsonPath("$.rows[0].canEdit").value(false))
                .andExpect(jsonPath("$.rows[0].canPropose").value(false));
    }

    @Test
    void secretariaSemCursoVinculadoNaoTemAcesso() throws Exception {
        mockMvc.perform(get("/api/finance/prices").with(secretaria(102, "semescopo")))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "fin", roles = {"FINANCEIRO"})
    void financeiroNaoTemAcesso() throws Exception {
        mockMvc.perform(get("/api/finance/prices")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "aluno", roles = {"ALUNO"})
    void alunoNaoTemAcesso() throws Exception {
        mockMvc.perform(get("/api/finance/prices")).andExpect(status().isForbidden());
    }

    @Test
    void semAutenticacaoNaoPassa() throws Exception {
        mockMvc.perform(get("/api/finance/prices")).andExpect(status().is4xxClientError());
    }

    // ------------------------------------------------------------------ chefe propõe, só admin decide

    @Test
    void chefePropoeEOValorVigenteNaoMuda() throws Exception {
        mockMvc.perform(post("/api/finance/prices/" + courseAId + "/proposals").with(secretaria(100, "chefe"))
                        .contentType(JSON).content("{\"value\":150.00,\"reason\":\"reajuste\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.currentValue").value(120.00))
                .andExpect(jsonPath("$.pending.value").value(150.00))
                .andExpect(jsonPath("$.pending.proposedBy").value("chefe"));
    }

    @Test
    void chefeNaoPropoeForaDoSeuEscopoNemEmLinhaGlobal() throws Exception {
        mockMvc.perform(post("/api/finance/prices/" + courseBId + "/proposals").with(secretaria(100, "chefe"))
                        .contentType(JSON).content("{\"value\":150.00}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/finance/prices/" + globalId + "/proposals").with(secretaria(100, "chefe"))
                        .contentType(JSON).content("{\"value\":150.00}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void secretariaDeLeituraNaoPropoe() throws Exception {
        mockMvc.perform(post("/api/finance/prices/" + courseAId + "/proposals").with(secretaria(101, "sec"))
                        .contentType(JSON).content("{\"value\":150.00}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void segundaPropostaPendenteIgualAoVigenteEValorInvalidoSaoRecusados() throws Exception {
        mockMvc.perform(post("/api/finance/prices/" + courseAId + "/proposals").with(secretaria(100, "chefe"))
                .contentType(JSON).content("{\"value\":150.00}")).andExpect(status().isCreated());
        mockMvc.perform(post("/api/finance/prices/" + courseAId + "/proposals").with(secretaria(100, "chefe"))
                .contentType(JSON).content("{\"value\":160.00}")).andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/finance/prices/" + courseAId + "/proposals").with(secretaria(100, "chefe"))
                .contentType(JSON).content("{\"value\":-1}")).andExpect(status().isBadRequest());
    }

    @Test
    void chefeNaoAlteraDiretoNemConfirma() throws Exception {
        mockMvc.perform(put("/api/finance/prices/" + courseAId).with(secretaria(100, "chefe"))
                        .contentType(JSON).content("{\"value\":1.00,\"version\":0}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/finance/prices/proposals/1/approve").with(secretaria(100, "chefe")))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/finance/prices/proposals/1/reject").with(secretaria(100, "chefe")))
                .andExpect(status().isForbidden());
    }

    @Test
    void chefeNaoVeHistoricoDeOutroCurso() throws Exception {
        mockMvc.perform(get("/api/finance/prices/" + courseBId + "/history").with(secretaria(100, "chefe")))
                .andExpect(status().isNotFound());
    }

    // ------------------------------------------------------------------ administrador decide

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void administradorConfirmaEOValorPassaAVigorerComHistorico() throws Exception {
        Long changeId = proposeAsHeadAsAdminContext("150.00");
        mockMvc.perform(post("/api/finance/prices/proposals/" + changeId + "/approve"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentValue").value(150.00))
                .andExpect(jsonPath("$.pending").doesNotExist());
        mockMvc.perform(get("/api/finance/prices/" + courseAId + "/history"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("APPROVED"))
                .andExpect(jsonPath("$[0].decidedBy").value("admin"));
        mockMvc.perform(post("/api/finance/prices/proposals/" + changeId + "/approve"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void administradorRejeitaEOValorNaoMuda() throws Exception {
        Long changeId = proposeAsHeadAsAdminContext("150.00");
        mockMvc.perform(post("/api/finance/prices/proposals/" + changeId + "/reject")
                        .contentType(JSON).content("{\"reason\":\"fora do orçamento\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentValue").value(120.00))
                .andExpect(jsonPath("$.pending").doesNotExist());
        mockMvc.perform(get("/api/finance/prices/" + courseAId + "/history"))
                .andExpect(jsonPath("$[0].status").value("REJECTED"))
                .andExpect(jsonPath("$[0].decisionReason").value("fora do orçamento"));
    }

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void alterarOValorDevolveAVersaoJaIncrementadaEPermiteEditarDeNovoComEla() throws Exception {
        String first = mockMvc.perform(put("/api/finance/prices/" + globalId).contentType(JSON)
                        .content("{\"value\":101.00,\"version\":0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(1))
                .andReturn().getResponse().getContentAsString();
        // Reaproveitar a versão da própria resposta (como faz quem usa a API direto) não pode dar falso conflito.
        mockMvc.perform(put("/api/finance/prices/" + globalId).contentType(JSON)
                        .content("{\"value\":102.00,\"version\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(2))
                .andExpect(jsonPath("$.currentValue").value(102.00));
        mockMvc.perform(put("/api/finance/prices/" + globalId).contentType(JSON)
                        .content("{\"value\":103.00,\"version\":0}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void confirmarPropostaDevolveAVersaoJaIncrementada() throws Exception {
        Long changeId = proposeAsHeadAsAdminContext("150.00");
        mockMvc.perform(post("/api/finance/prices/proposals/" + changeId + "/approve"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(1));
    }

    /** Cria a proposta direto no repositório (o contexto de segurança do teste é o do administrador). */
    private Long proposeAsHeadAsAdminContext(String value) {
        ChargePriceChange c = new ChargePriceChange();
        c.setChargePriceId(courseAId);
        c.setProposedValue(new BigDecimal(value));
        c.setProposedBy("chefe");
        return changeRepository.saveAndFlush(c).getId();
    }

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void alteracaoDiretaDoAdminValidaVersaoValorESuperaProposta() throws Exception {
        Long changeId = proposeAsHeadAsAdminContext("150.00");
        long version = priceRepository.findById(courseAId).orElseThrow().getVersion();

        mockMvc.perform(put("/api/finance/prices/" + courseAId).contentType(JSON)
                        .content("{\"value\":-5,\"version\":" + version + "}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(put("/api/finance/prices/" + courseAId).contentType(JSON)
                        .content("{\"value\":1.239,\"version\":" + version + "}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(put("/api/finance/prices/" + courseAId).contentType(JSON)
                        .content("{\"value\":200.00,\"version\":" + (version + 7) + "}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(put("/api/finance/prices/" + courseAId).contentType(JSON)
                        .content("{\"value\":200.00,\"version\":" + version + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentValue").value(200.00))
                .andExpect(jsonPath("$.pending").doesNotExist());

        mockMvc.perform(get("/api/finance/prices/" + courseAId + "/history"))
                .andExpect(jsonPath("$[0].id").value(changeId.intValue()))
                .andExpect(jsonPath("$[0].status").value("REJECTED"));
    }
}
