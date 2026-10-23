package com.portalcursos.ng02.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;

import com.portalcursos.ng02.model.ChargeAmountException;
import com.portalcursos.ng02.model.ChargePrice;
import com.portalcursos.ng02.model.ChargePriceChange;
import com.portalcursos.ng02.model.EAcademicLevel;
import com.portalcursos.ng02.model.StaffCourseScope;
import com.portalcursos.ng02.repository.ChargeAmountExceptionRepository;
import com.portalcursos.ng02.repository.ChargePriceChangeRepository;
import com.portalcursos.ng02.repository.ChargePriceRepository;
import com.portalcursos.ng02.repository.StaffCourseScopeRepository;
import com.portalcursos.ng02.service.UserDetailsImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * Matriz de permissões da tabela de valores e dos pedidos de exceção. Regra de negócio travada aqui:
 * <b>qualquer alteração de valor sugerida pela secretaria só se efetiva por um administrador</b> (ADMIN ou
 * ROOT_MASTER). Cada rota que efetiva ou decide é chamada por todos os perfis; só os administradores passam do
 * portão de acesso (os demais recebem 403), e sem login recebem 401.
 */
@SpringBootTest(properties = {
    "SPRING_DATASOURCE_URL=jdbc:h2:mem:chargepermissionmatrixtestdb;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
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
class ChargePermissionMatrixIntegrationTest {

    private static final UUID COURSE_A = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final String JSON = MediaType.APPLICATION_JSON_VALUE;
    private static final int ROUTE_COUNT = 10;

    @Autowired private MockMvc mockMvc;
    @Autowired private ChargePriceRepository priceRepository;
    @Autowired private ChargePriceChangeRepository changeRepository;
    @Autowired private ChargeAmountExceptionRepository exceptionRepository;
    @Autowired private StaffCourseScopeRepository scopeRepository;

    private Long priceId;
    private Long changeId;
    private Long exceptionId;
    private Long scopeId;

    @BeforeEach
    void seed() {
        exceptionRepository.deleteAll();
        changeRepository.deleteAll();
        priceRepository.deleteAll();
        scopeRepository.deleteAll();

        ChargePrice price = new ChargePrice();
        price.setCode("NADA_CONSTA");
        price.setCategory("Categoria");
        price.setName("Certidão de nada consta");
        price.setCurrentValue(new BigDecimal("100.00"));
        priceId = priceRepository.saveAndFlush(price).getId();

        ChargePriceChange change = new ChargePriceChange();
        change.setChargePriceId(priceId);
        change.setProposedValue(new BigDecimal("80.00"));
        change.setProposedBy("chefe");
        changeId = changeRepository.saveAndFlush(change).getId();

        ChargeAmountException exception = new ChargeAmountException();
        exception.setStudentId(1L);
        exception.setAcademicLevel(EAcademicLevel.GRADUATION);
        exception.setChargePriceId(priceId);
        exception.setTableAmount(new BigDecimal("100.00"));
        exception.setRequestedAmount(new BigDecimal("50.00"));
        exception.setDueDate(LocalDate.of(2026, 12, 1));
        exception.setReason("Bolsa parcial concedida pela direção");
        exception.setRequestedBy("sec.pedido");
        exceptionId = exceptionRepository.saveAndFlush(exception).getId();

        StaffCourseScope head = new StaffCourseScope();
        head.setStaffId(100L);
        head.setCourseId(COURSE_A);
        head.setHead(true);
        scopeId = scopeRepository.saveAndFlush(head).getId();
        StaffCourseScope plain = new StaffCourseScope();
        plain.setStaffId(101L);
        plain.setCourseId(COURSE_A);
        plain.setHead(false);
        scopeRepository.saveAndFlush(plain);
    }

    /** Perfis testados: nome, papel, id do usuário e se é administrador. */
    private record Profile(String name, String role, long userId, boolean admin) {
        RequestPostProcessor as() {
            return user(new UserDetailsImpl(userId, name, name + "@x.com", "x", List.of(new SimpleGrantedAuthority("ROLE_" + role))));
        }

        @Override
        public String toString() {
            return name;
        }
    }

    private static final List<Profile> PROFILES = List.of(
            new Profile("admin", "ADMIN", 1, true),
            new Profile("rootmaster", "ROOT_MASTER", 2, true),
            new Profile("chefe-de-secretaria", "SECRETARIA", 100, false),
            new Profile("secretaria-leitura", "SECRETARIA", 101, false),
            new Profile("secretaria-sem-curso", "SECRETARIA", 102, false),
            new Profile("financeiro", "FINANCEIRO", 200, false),
            new Profile("coordenador", "COORDENADOR", 201, false),
            new Profile("matricula", "MATRICULA", 202, false),
            new Profile("aluno", "ALUNO", 300, false));

    /** Uma rota que efetiva ou decide uma alteração de valor, com o corpo mínimo e o método HTTP. */
    private record Route(String label, HttpMethod method, String path, String body) {
        @Override
        public String toString() {
            return label;
        }
    }

    private List<Route> routes() {
        return List.of(
                new Route("alterar valor direto", HttpMethod.PUT, "/api/finance/prices/" + priceId,
                        "{\"value\":90.00,\"version\":0}"),
                new Route("criar valor por curso", HttpMethod.POST, "/api/finance/prices/course-rows",
                        "{\"sourceId\":" + priceId + ",\"courseId\":\"" + COURSE_A + "\",\"value\":70.00}"),
                new Route("confirmar proposta de valor", HttpMethod.POST,
                        "/api/finance/prices/proposals/" + changeId + "/approve", null),
                new Route("rejeitar proposta de valor", HttpMethod.POST,
                        "/api/finance/prices/proposals/" + changeId + "/reject", "{\"reason\":\"não\"}"),
                new Route("listar vínculos secretaria-curso", HttpMethod.GET, "/api/finance/price-scopes", null),
                new Route("criar vínculo secretaria-curso", HttpMethod.POST, "/api/finance/price-scopes",
                        "{\"staffId\":100,\"courseId\":\"" + COURSE_A + "\",\"head\":true}"),
                new Route("remover vínculo secretaria-curso", HttpMethod.DELETE, "/api/finance/price-scopes/" + scopeId, null),
                new Route("aprovar exceção de valor", HttpMethod.POST,
                        "/api/finance/charge-exceptions/" + exceptionId + "/approve", null),
                new Route("rejeitar exceção de valor", HttpMethod.POST,
                        "/api/finance/charge-exceptions/" + exceptionId + "/reject", "{\"reason\":\"não\"}"),
                new Route("excluir (perdoar) cobrança", HttpMethod.DELETE, "/api/finance/invoices/999999", null));
    }

    private int perform(Route route, RequestPostProcessor who) throws Exception {
        MockHttpServletRequestBuilder builder = MockMvcRequestBuilders.request(route.method(), route.path());
        if (route.body() != null) {
            builder.contentType(JSON).content(route.body());
        } else if (route.method() == HttpMethod.POST) {
            builder.contentType(JSON).content("{}");
        }
        if (who != null) {
            builder.with(who);
        }
        return mockMvc.perform(builder).andReturn().getResponse().getStatus();
    }

    static Stream<Arguments> profileAndRoute() {
        // As rotas dependem dos ids semeados; a lista é montada na hora do teste. Aqui só o produto perfil x índice.
        return PROFILES.stream().flatMap(p -> java.util.stream.IntStream.range(0, ROUTE_COUNT).mapToObj(i -> Arguments.of(p, i)));
    }

    @ParameterizedTest(name = "{0} x rota #{1}")
    @MethodSource("profileAndRoute")
    void soAdministradorPassaDoPortaoDeAcessoDasRotasQueEfetivamAlteracoes(Profile profile, int routeIndex) throws Exception {
        Route route = routes().get(routeIndex);

        int status = perform(route, profile.as());

        if (profile.admin()) {
            assertNotEquals(403, status, "administrador deve passar do portão em: " + route);
            assertNotEquals(401, status, "administrador autenticado não pode receber 401 em: " + route);
        } else {
            assertEquals(403, status, profile.name() + " NÃO pode: " + route);
        }
    }

    @ParameterizedTest(name = "sem login x {0}")
    @MethodSource("allRouteIndexes")
    void semLoginTodasAsRotasDao401(int routeIndex) throws Exception {
        Route route = routes().get(routeIndex);

        assertEquals(401, perform(route, null), "sem login deve dar 401 em: " + route);
    }

    static Stream<Arguments> allRouteIndexes() {
        return java.util.stream.IntStream.range(0, ROUTE_COUNT).mapToObj(Arguments::of);
    }

    // --- Propor valor: a secretaria sugere, mas só a chefe do curso, e nada vira vigente ---

    @Test
    void soAChefeDoCursoConsegueProporEAPropostaNaoMudaOValorVigente() throws Exception {
        // Linha do curso A (a global não aceita proposta de chefe).
        ChargePrice courseRow = new ChargePrice();
        courseRow.setCode("NADA_CONSTA");
        courseRow.setCategory("Categoria");
        courseRow.setName("Certidão de nada consta");
        courseRow.setCourseId(COURSE_A);
        courseRow.setCurrentValue(new BigDecimal("100.00"));
        Long courseRowId = priceRepository.saveAndFlush(courseRow).getId();
        String path = "/api/finance/prices/" + courseRowId + "/proposals";
        String body = "{\"value\":60.00,\"reason\":\"ajuste do semestre\"}";
        Route propose = new Route("propor valor", HttpMethod.POST, path, body);

        for (Profile p : PROFILES) {
            if (p.name().equals("chefe-de-secretaria")) {
                continue;
            }
            int denied = perform(propose, p.as());
            // Quem é admin pode propor (a regra de "não efetivar" vale para a secretaria); os demais, nunca.
            if (!p.admin()) {
                assertEquals(403, denied, p.name() + " não pode propor");
            }
        }
        assertEquals(201, perform(propose, PROFILES.get(2).as()), "a chefe do curso propõe");
        assertEquals(0, priceRepository.findById(courseRowId).orElseThrow().getCurrentValue().compareTo(new BigDecimal("100.00")),
                "a proposta não altera o valor vigente: só a confirmação do administrador altera");
    }
}
