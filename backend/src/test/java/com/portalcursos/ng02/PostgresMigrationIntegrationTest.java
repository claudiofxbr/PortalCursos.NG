package com.portalcursos.ng02;

import com.portalcursos.ng02.model.EPaymentStatus;
import com.portalcursos.ng02.model.Payment;
import com.portalcursos.ng02.model.Student;
import com.portalcursos.ng02.repository.PaymentRepository;
import com.portalcursos.ng02.repository.StudentRepository;
import com.portalcursos.ng02.service.PaymentService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Roda as migrations Flyway reais contra um Postgres de verdade (Testcontainers), em vez
 * do H2 que os demais testes usam por velocidade. Cobre o que ficava fora do CI (achado da
 * auditoria — "hoje o CI roda Flyway desabilitado + H2"): migrations V1..V21 aplicando sem
 * falha, o comportamento real de {@code @SQLRestriction} (soft-delete) e o índice único
 * parcial de {@code students.cpf/email} (V21) no dialeto Postgres de verdade — nenhum dos
 * dois é exercitado fielmente pelo H2 em modo de compatibilidade. Também cobre a
 * regressão do bug de {@code @SQLDelete}+{@code @Version} em {@code Payment} (achado P0
 * corrigido nesta rodada — ver {@link #deleteChargeDesativaPagamentoSemErroContraPostgresReal()}).
 *
 * <p>{@code DatabaseConfig} declara o bean {@code DataSource} manualmente a partir de
 * {@code DataSourceProperties} (para tratar URLs estilo Render/Neon), então não usa o
 * {@code JdbcConnectionDetails} que {@code @ServiceConnection} injetaria — por isso as
 * propriedades do container são publicadas via {@link DynamicPropertySource} clássico, que
 * {@code DataSourceProperties} lê normalmente. As demais propriedades sobrescrevem
 * {@code src/test/resources/application.properties} (H2 + Flyway desabilitado, usado pelos
 * outros testes por velocidade) só nesta classe.
 */
@Testcontainers
@SpringBootTest(properties = {
    "APP_JWT_SECRET=ZXhhbXBsZS1zZWNyZXQta2V5LXdpdGgtZW5vdWdoLWxlbmd0aC1mb3ItYmFzZTY0LWVuY29kaW5nLXByb3Blcmx5",
    "APP_JWT_EXPIRATION=900000",
    "APP_ROOT_PASSWORD=TestRootPass123!",
    "APP_ADMIN_PASSWORD=TestAdminPass123!",
    "spring.flyway.enabled=true",
    "spring.jpa.hibernate.ddl-auto=none",
    "spring.jpa.database-platform=org.hibernate.dialect.PostgreSQLDialect"
})
@Transactional
@DirtiesContext
class PostgresMigrationIntegrationTest {

    @Container
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", postgres::getDriverClassName);
    }

    @Autowired
    private StudentRepository studentRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private jakarta.persistence.EntityManager entityManager;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private PaymentService paymentService;

    @Test
    void migrationsAplicamSemFalhaContraPostgresReal() {
        Integer failed = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE success = false", Integer.class);
        assertEquals(0, failed, "Nenhuma migration deve ter falhado ao rodar contra Postgres real");

        Integer applied = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE success = true", Integer.class);
        assertTrue(applied != null && applied > 0, "Deve haver ao menos uma migration aplicada");
    }

    // Soft-delete via setActive(false) + save(), não via repository.delete()/deleteById():
    // é assim que a aplicação real desativa Student (ver UserService.deleteUser). O
    // @SQLDelete de Student nunca é exercitado em produção (nenhum código chama
    // studentRepository.delete()) e tem o mesmo bug do Payment abaixo (SQL customizado não
    // bate com o parâmetro de @Version que o Hibernate injeta) — como é código morto (não
    // afeta nenhum fluxo real), ficou registrado como achado sem correção por ora.
    @Test
    void sqlRestrictionEscondeStudentDesativado() {
        Student saved = studentRepository.saveAndFlush(
                buildStudent("11111111111", "aluno-restricao@test.com", "REG-RESTR-001"));
        Long id = saved.getId();

        saved.setActive(false);
        studentRepository.saveAndFlush(saved);
        // findById de uma entidade já presente no persistence context (1st-level cache)
        // devolveria a instância em memória direto, sem SQL — e sem @SQLRestriction, que só
        // se aplica a queries de verdade. Limpa a sessão para forçar um SELECT real.
        entityManager.clear();

        assertTrue(studentRepository.findById(id).isEmpty(),
                "@SQLRestriction(active = true) deve esconder o registro soft-deletado das queries do JPA");

        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM students WHERE id = ? AND active = false", Integer.class, id);
        assertEquals(1, count, "O registro deve continuar na tabela, só marcado como inativo (soft-delete real)");
    }

    @Test
    void indiceParcialPermiteReusarCpfDeAlunoDesativado() {
        String cpf = "22222222222";
        Student first = studentRepository.saveAndFlush(buildStudent(cpf, "primeiro@test.com", "REG-CPF-001"));
        first.setActive(false);
        studentRepository.saveAndFlush(first);

        assertDoesNotThrow(
                () -> studentRepository.saveAndFlush(buildStudent(cpf, "segundo@test.com", "REG-CPF-002")),
                "V21: índice único parcial (WHERE active = true) deve permitir reusar CPF de aluno desativado");
    }

    @Test
    void indiceParcialAindaBloqueiaCpfDuplicadoEntreAtivos() {
        String cpf = "33333333333";
        studentRepository.saveAndFlush(buildStudent(cpf, "ativo1@test.com", "REG-CPF-003"));

        assertThrows(DataIntegrityViolationException.class,
                () -> studentRepository.saveAndFlush(buildStudent(cpf, "ativo2@test.com", "REG-CPF-004")),
                "Dois alunos ATIVOS com o mesmo CPF continuam proibidos pelo índice parcial");
    }

    // Regressão do achado P0: paymentRepository.delete() (via @SQLDelete) lançava
    // DataIntegrityViolationException sempre contra um Postgres real, porque o SQL
    // customizado não incluía o parâmetro de @Version que o Hibernate injeta para
    // entidades versionadas — endpoint DELETE /api/finance/invoices/{id} retornava 500 em
    // produção. Corrigido removendo @SQLDelete de Payment e trocando deleteCharge para
    // soft-delete explícito (setActive(false) + save()). Só um teste com banco real
    // (Testcontainers) pega esse tipo de bug — um mock de PaymentRepository não executa
    // SQL de verdade, por isso passou despercebido antes.
    @Test
    void deleteChargeDesativaPagamentoSemErroContraPostgresReal() {
        Payment saved = paymentRepository.saveAndFlush(Payment.builder()
                .amount(new java.math.BigDecimal("150.00"))
                .dueDate(java.time.LocalDate.now().plusDays(10))
                .status(EPaymentStatus.PENDING)
                .build());

        assertDoesNotThrow(() -> paymentService.deleteCharge(saved.getId()));

        // deleteCharge usa paymentRepository.save() (flush implícito só no commit da
        // transação) — força a escrita agora para poder verificar via jdbcTemplate/limpar
        // o persistence context sem perder a mudança ainda não sincronizada.
        entityManager.flush();
        entityManager.clear();
        assertTrue(paymentRepository.findById(saved.getId()).isEmpty(),
                "@SQLRestriction(active = true) deve esconder o pagamento desativado");

        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM payments WHERE id = ? AND active = false", Integer.class, saved.getId());
        assertEquals(1, count, "O pagamento deve continuar na tabela, só marcado como inativo");
    }

    private Student buildStudent(String cpf, String email, String registrationNumber) {
        return Student.builder()
                .registrationNumber(registrationNumber)
                .fullName("Aluno Teste")
                .email(email)
                .cpf(cpf)
                .build();
    }
}
