package com.portalcursos.ng02;

import com.portalcursos.ng02.controller.RepairController;
import com.portalcursos.ng02.controller.StaffMemberController;
import com.portalcursos.ng02.model.DataDeletionRequest;
import com.portalcursos.ng02.model.EPaymentStatus;
import com.portalcursos.ng02.model.Payment;
import com.portalcursos.ng02.model.RepairTicket;
import com.portalcursos.ng02.model.Role;
import com.portalcursos.ng02.model.StaffMember;
import com.portalcursos.ng02.model.Student;
import com.portalcursos.ng02.model.User;
import com.portalcursos.ng02.repository.DataDeletionRequestRepository;
import com.portalcursos.ng02.repository.PaymentRepository;
import com.portalcursos.ng02.repository.RepairRepository;
import com.portalcursos.ng02.repository.RoleRepository;
import com.portalcursos.ng02.repository.StaffMemberRepository;
import com.portalcursos.ng02.repository.StudentRepository;
import com.portalcursos.ng02.repository.UserRepository;
import com.portalcursos.ng02.service.PaymentService;
import com.portalcursos.ng02.service.UserDetailsImpl;
import com.portalcursos.ng02.service.UserService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.util.Set;

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

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private DataDeletionRequestRepository dataDeletionRequestRepository;

    @Autowired
    private UserService userService;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private StaffMemberRepository staffMemberRepository;

    @Autowired
    private RepairRepository repairRepository;

    @Autowired
    private RepairController repairController;

    @Autowired
    private StaffMemberController staffMemberController;

    @AfterEach
    void limpaContextoDeSeguranca() {
        SecurityContextHolder.clearContext();
    }

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

    // Regressão do achado P1: RepairController.deleteTicket fazia hard-delete real
    // (repairRepository.delete(ticket)), inconsistente com o padrão de soft-delete usado no
    // resto do sistema (Payment/Student/StaffMember) e apagando o histórico/auditoria do
    // chamado de manutenção. Corrigido trocando para setActive(false) + save(), mesmo padrão
    // de PaymentService.deleteCharge/UserService.deleteUser — RepairTicket já tem
    // @SQLRestriction("active = true"), então o soft-delete já funciona de forma
    // transparente com as queries de listagem existentes, sem precisar mexer no repositório.
    @Test
    void deleteTicketDesativaChamadoSemErroContraPostgresReal() {
        RepairTicket saved = repairRepository.saveAndFlush(RepairTicket.builder()
                .title("Ar-condicionado quebrado")
                .description("Não liga")
                .location("Lab 03")
                .status(RepairTicket.ERepairStatus.OPEN)
                .build());
        Long id = saved.getId();

        // RepairController.deleteTicket tem @PreAuthorize(AUTHORIZED_ROLES) — chamar o bean
        // gerenciado diretamente (não via MockMvc) ainda passa pelo interceptor de method
        // security, então precisa de um Authentication válido no contexto (mesmo padrão da
        // regressão de deleteUser mais abaixo nesta classe).
        Role adminRole = roleRepository.findByName(Role.ERole.ROLE_ADMIN).orElseThrow();
        User operator = userRepository.saveAndFlush(User.builder()
                .username("operador-repair-teste")
                .email("operador-repair-teste@test.com")
                .password("hash-qualquer")
                .roles(Set.of(adminRole))
                .build());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        UserDetailsImpl.build(operator), null, UserDetailsImpl.build(operator).getAuthorities()));

        assertDoesNotThrow(() -> repairController.deleteTicket(id));

        entityManager.flush();
        entityManager.clear();
        assertTrue(repairRepository.findById(id).isEmpty(),
                "@SQLRestriction(active = true) deve esconder o chamado desativado");

        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM repair_tickets WHERE id = ? AND active = false", Integer.class, id);
        assertEquals(1, count, "O chamado deve continuar na tabela, só marcado como inativo (soft-delete real)");
    }

    // Regressão do achado P1: StaffMemberController.deleteStaff fazia hard-delete real
    // (staffRepository.delete(staff)) — mesmo bug de @SQLDelete sem o parâmetro de @Version
    // já corrigido em Payment/RepairTicket, então toda chamada lançava
    // DataIntegrityViolationException. Corrigido trocando para setActive(false) + save().
    // StaffMember não tem @SQLRestriction (é referenciado como `creator` em todo registro
    // auditado), então o soft-delete não esconde o registro de findById — só marca
    // active=false; as listagens já filtram explicitamente via findAllByActiveTrue.
    @Test
    void deleteStaffDesativaColaboradorSemErroContraPostgresReal() {
        Role staffRole = roleRepository.findByName(Role.ERole.ROLE_SECRETARIA).orElseThrow();
        User linkedUser = userRepository.saveAndFlush(User.builder()
                .username("colaborador-delete-teste")
                .email("colaborador-delete-teste@test.com")
                .password("hash-qualquer")
                .roles(Set.of(staffRole))
                .build());

        StaffMember staff = new StaffMember();
        staff.setUser(linkedUser);
        staff.setFullName("Colaborador Para Remover");
        staff.setPosition("Secretaria");
        staff.setDepartment("Acadêmico");
        staff.setActive(true);
        StaffMember saved = staffMemberRepository.saveAndFlush(staff);
        Long id = saved.getId();

        // StaffMemberController.deleteStaff tem @PreAuthorize — mesmo padrão de autenticação
        // dos outros testes de soft-delete direto no controller nesta classe.
        Role adminRole = roleRepository.findByName(Role.ERole.ROLE_ADMIN).orElseThrow();
        User operator = userRepository.saveAndFlush(User.builder()
                .username("operador-staff-teste")
                .email("operador-staff-teste@test.com")
                .password("hash-qualquer")
                .roles(Set.of(adminRole))
                .build());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        UserDetailsImpl.build(operator), null, UserDetailsImpl.build(operator).getAuthorities()));

        assertDoesNotThrow(() -> staffMemberController.deleteStaff(id));

        entityManager.flush();
        entityManager.clear();

        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM staff_members WHERE id = ? AND active = false", Integer.class, id);
        assertEquals(1, count, "O colaborador deve continuar na tabela, só marcado como inativo (soft-delete real)");
    }

    // Regressão do achado: userRepository.deleteById() (hard delete, usado por
    // UserService.deleteUser) lançava DataIntegrityViolationException sempre que existisse
    // um DataDeletionRequest.user_id (ou repair_tickets.reported_by_id) apontando para o
    // usuário, porque as FKs para users(id) ficavam sem ON DELETE (RESTRICT/NO ACTION por
    // padrão) — diferente do padrão ON DELETE SET NULL já usado no resto do schema para
    // referências a users(id) (ver V9, students.user_id/staff_members.user_id). Corrigido na
    // V22, recriando essas duas FKs com ON DELETE SET NULL.
    @Test
    void deleteUserNaoQuebraComDataDeletionRequestApontandoParaEleContraPostgresReal() {
        User user = userRepository.saveAndFlush(User.builder()
                .username("usuario-fk-teste")
                .email("usuario-fk-teste@test.com")
                .password("hash-qualquer")
                .build());
        Long userId = user.getId();

        DataDeletionRequest request = dataDeletionRequestRepository.saveAndFlush(
                DataDeletionRequest.builder()
                        .userId(userId)
                        .requestedUsername(user.getUsername())
                        .build());
        Long requestId = request.getId();

        assertDoesNotThrow(() -> userRepository.deleteById(userId));
        entityManager.flush();
        entityManager.clear();

        assertTrue(userRepository.findById(userId).isEmpty(), "Usuário deve ter sido removido (hard delete)");

        DataDeletionRequest reloaded = dataDeletionRequestRepository.findById(requestId).orElseThrow();
        assertNull(reloaded.getUserId(),
                "V22: ON DELETE SET NULL deve zerar user_id em vez de bloquear a exclusão do usuário");
    }

    // Regressão do achado real de produção (2026-09-13): UserService.deleteUser desativava o
    // StaffMember vinculado com staffMemberRepository.save() (sem flush) e, em seguida,
    // chamava userRepository.deleteById() na MESMA transação. StaffMember.user usa @MapsId
    // (compartilha a PK do User) — como o UPDATE do StaffMember ainda estava pendente de
    // flush quando o User foi marcado para remoção, o Hibernate, ao tentar resolver essa
    // associação no commit, encontrava uma referência a uma instância de User já removida e
    // lançava TransientPropertyValueException. Resultado real em produção: o log dizia
    // "Usuário deletado" (a chamada a deleteById não lança nada por si só), mas o COMMIT da
    // transação falhava logo em seguida — a operação inteira sofria rollback (nenhum dado
    // ficou corrompido) e o admin via um erro 500 sem explicação ao tentar remover qualquer
    // colaborador com StaffMember ativo. Corrigido trocando save() por saveAndFlush() nas
    // duas desativações (StaffMember e Student) dentro de deleteUser, garantindo que essas
    // escritas sejam concluídas antes do delete do User. Só um teste com banco real
    // (Testcontainers) pega esse tipo de bug de ordenação de flush do Hibernate — os testes
    // mockados de UserServiceTest não executam SQL de verdade.
    @Test
    void deleteUserComStaffMemberVinculadoNaoLancaExcecaoContraPostgresReal() {
        Role rootRole = roleRepository.findByName(Role.ERole.ROLE_ROOT_MASTER).orElseThrow();
        Role staffRole = roleRepository.findByName(Role.ERole.ROLE_SECRETARIA).orElseThrow();

        User operator = userRepository.saveAndFlush(User.builder()
                .username("operador-root-teste")
                .email("operador-root-teste@test.com")
                .password("hash-qualquer")
                .roles(Set.of(rootRole))
                .build());

        User target = userRepository.saveAndFlush(User.builder()
                .username("colaborador-fk-teste")
                .email("colaborador-fk-teste@test.com")
                .password("hash-qualquer")
                .roles(Set.of(staffRole))
                .build());
        Long targetId = target.getId();

        StaffMember staff = new StaffMember();
        staff.setUser(target);
        staff.setFullName("Colaborador Teste");
        staff.setPosition("Secretaria");
        staff.setDepartment("Acadêmico");
        staff.setActive(true);
        staffMemberRepository.saveAndFlush(staff);

        // Simula uma requisição HTTP nova (a remoção real acontece numa transação separada da
        // criação): limpa o contexto de persistência para que deleteUser() não encontre o
        // objeto StaffMember/User desta configuração ainda anexado à sessão.
        entityManager.clear();

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        UserDetailsImpl.build(operator), null, UserDetailsImpl.build(operator).getAuthorities()));

        assertDoesNotThrow(() -> userService.deleteUser(targetId),
                "Remover um usuário com StaffMember ativo vinculado não deve lançar TransientPropertyValueException");

        entityManager.flush();
        entityManager.clear();

        assertTrue(userRepository.findById(targetId).isEmpty(), "Usuário deve ter sido removido (hard delete)");

        Integer staffAtivo = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM staff_members WHERE id = ? AND active = true", Integer.class, targetId);
        assertEquals(0, staffAtivo, "StaffMember vinculado deve ter sido desativado antes da remoção do usuário");
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
