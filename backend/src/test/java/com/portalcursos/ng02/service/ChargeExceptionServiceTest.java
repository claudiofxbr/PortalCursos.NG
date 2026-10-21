package com.portalcursos.ng02.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.portalcursos.ng02.dto.ChargeCatalogDtos.ValueSource;
import com.portalcursos.ng02.dto.ChargeExceptionDtos.DecisionRequest;
import com.portalcursos.ng02.dto.ChargeExceptionDtos.ExceptionDto;
import com.portalcursos.ng02.dto.ChargeExceptionDtos.ExceptionRequest;
import com.portalcursos.ng02.exception.BusinessException;
import com.portalcursos.ng02.model.ChargeAmountException;
import com.portalcursos.ng02.model.ChargeAmountException.Status;
import com.portalcursos.ng02.model.ChargePrice;
import com.portalcursos.ng02.model.EAcademicLevel;
import com.portalcursos.ng02.model.EPaymentCategory;
import com.portalcursos.ng02.model.ESecretaryProcessType;
import com.portalcursos.ng02.model.Payment;
import com.portalcursos.ng02.model.Student;
import com.portalcursos.ng02.repository.ChargeAmountExceptionRepository;
import com.portalcursos.ng02.repository.PaymentRepository;
import com.portalcursos.ng02.repository.PostgradStudentRepository;
import com.portalcursos.ng02.repository.StudentRepository;
import com.portalcursos.ng02.service.ChargePriceAccessService.Access;
import com.portalcursos.ng02.service.ChargePriceAccessService.Level;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Pedido de exceção ao valor da tabela: quem pode pedir, quem decide e o que é emitido. */
@ExtendWith(MockitoExtension.class)
class ChargeExceptionServiceTest {

    private static final UUID COURSE = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID OTHER_COURSE = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");

    @Mock private ChargeAmountExceptionRepository repository;
    @Mock private StudentRepository studentRepository;
    @Mock private PostgradStudentRepository postgradStudentRepository;
    @Mock private PaymentRepository paymentRepository;
    @Mock private ChargeCatalogService catalogService;
    @Mock private ChargePriceAccessService accessService;
    @Mock private ChargePolicy policy;
    @Mock private AuditService auditService;
    @InjectMocks private ChargeExceptionService service;

    private Student student;
    private ChargeCatalogService.Resolved table;

    @BeforeEach
    void setUp() {
        student = Student.builder().id(1L).fullName("Maria").fotoMatricula("foto.jpg").build();
        ChargePrice row = new ChargePrice();
        row.setId(7L);
        row.setName("Certidão de nada consta");
        table = new ChargeCatalogService.Resolved(row, new BigDecimal("100.00"), ValueSource.GLOBAL,
                EPaymentCategory.INTERNAL_PROCESS, ESecretaryProcessType.CERTIFICATE);
    }

    private static Access secretaria(String actor, UUID... courses) {
        return new Access(Level.SECRETARIA, Set.of(courses), Set.of(), actor);
    }

    private static Access admin(String actor) {
        return new Access(Level.ADMIN, Set.of(), Set.of(), actor);
    }

    private static ExceptionRequest request(String amount, String reason) {
        return new ExceptionRequest(1L, EAcademicLevel.GRADUATION, 7L, LocalDate.of(2026, 12, 1), null,
                amount == null ? null : new BigDecimal(amount), reason);
    }

    private void stubRequestPath(Access access) {
        when(accessService.current()).thenReturn(access);
        when(studentRepository.findById(1L)).thenReturn(Optional.of(student));
        when(catalogService.resolve(7L, student, EAcademicLevel.GRADUATION)).thenReturn(table);
    }

    private ChargeAmountException pending(String requestedBy) {
        ChargeAmountException e = new ChargeAmountException();
        e.setId(9L);
        e.setStudentId(1L);
        e.setAcademicLevel(EAcademicLevel.GRADUATION);
        e.setChargePriceId(7L);
        e.setTableAmount(new BigDecimal("100.00"));
        e.setRequestedAmount(new BigDecimal("60.00"));
        e.setDueDate(LocalDate.of(2026, 12, 1));
        e.setReason("Bolsa parcial concedida pela direção");
        e.setRequestedBy(requestedBy);
        return e;
    }

    // --- pedir ---

    @Test
    void secretariaDoCursoDoAlunoCriaPedidoPendenteSemCobrarNada() {
        stubRequestPath(secretaria("ana", COURSE));
        when(catalogService.courseIdOf(student)).thenReturn(COURSE);
        when(repository.save(any(ChargeAmountException.class))).thenAnswer(inv -> inv.getArgument(0));

        ExceptionDto dto = service.request(request("60.00", "Bolsa parcial concedida pela direção"));

        assertEquals(Status.PENDING, dto.status());
        assertEquals(new BigDecimal("100.00"), dto.tableAmount());
        assertEquals(new BigDecimal("60.00"), dto.requestedAmount());
        assertEquals("ana", dto.requestedBy());
        verify(paymentRepository, never()).save(any());
    }

    @Test
    void secretariaDeOutroCursoNaoPodePedir() {
        stubRequestPath(secretaria("ana", OTHER_COURSE));
        when(catalogService.courseIdOf(student)).thenReturn(COURSE);

        assertThrows(AccessDeniedException.class, () -> service.request(request("60.00", "Bolsa parcial concedida")));
        verify(repository, never()).save(any());
    }

    @Test
    void alunoSemCursoSoPodeTerPedidoDeAdministrador() {
        stubRequestPath(secretaria("ana", COURSE));
        when(catalogService.courseIdOf(student)).thenReturn(null);

        assertThrows(AccessDeniedException.class, () -> service.request(request("60.00", "Bolsa parcial concedida")));
    }

    @Test
    void perfilSemAcessoNaoPodePedir() {
        when(accessService.current()).thenReturn(new Access(Level.NONE, Set.of(), Set.of(), "fin"));

        assertThrows(AccessDeniedException.class, () -> service.request(request("60.00", "Bolsa parcial concedida")));
    }

    @Test
    void administradorPodePedirParaQualquerCurso() {
        stubRequestPath(admin("adm1"));
        when(repository.save(any(ChargeAmountException.class))).thenAnswer(inv -> inv.getArgument(0));

        assertEquals(Status.PENDING, service.request(request("0.00", "Isenção concedida pelo conselho")).status());
    }

    @Test
    void valorIgualAoDaTabelaEhRecusado() {
        stubRequestPath(admin("adm1"));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.request(request("100.00", "Sem diferença real do valor")));
        assertTrue(ex.getMessage().contains("igual ao da tabela"));
    }

    @Test
    void justificativaCurtaOuAusenteEhRecusada() {
        assertThrows(BusinessException.class, () -> service.request(request("60.00", "curto")));
        assertThrows(BusinessException.class, () -> service.request(request("60.00", null)));
    }

    @Test
    void valorNegativoOuAusenteEhRecusado() {
        assertThrows(BusinessException.class, () -> service.request(request("-1.00", "Justificativa válida longa")));
        assertThrows(BusinessException.class, () -> service.request(request(null, "Justificativa válida longa")));
    }

    @Test
    void jaExistindoPedidoPendenteParaOMesmoAlunoEProcedimentoEhRecusado() {
        stubRequestPath(admin("adm1"));
        when(repository.existsByStudentIdAndChargePriceIdAndStatus(1L, 7L, Status.PENDING)).thenReturn(true);

        assertThrows(BusinessException.class, () -> service.request(request("60.00", "Bolsa parcial concedida")));
        verify(repository, never()).save(any());
    }

    // --- decidir ---

    @Test
    void outroAdministradorAprovaEAcobrancaNasceComOValorAutorizado() {
        ChargeAmountException entity = pending("ana");
        when(accessService.current()).thenReturn(admin("adm2"));
        when(repository.findById(9L)).thenReturn(Optional.of(entity));
        when(studentRepository.findById(1L)).thenReturn(Optional.of(student));
        when(catalogService.resolve(7L, student, EAcademicLevel.GRADUATION)).thenReturn(table);
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> {
            Payment p = inv.getArgument(0);
            p.setId(55L);
            return p;
        });

        ExceptionDto dto = service.approve(9L, new DecisionRequest("Conferido"));

        assertEquals(Status.APPROVED, dto.status());
        assertEquals("adm2", dto.decidedBy());
        assertEquals(55L, dto.paymentId());
        verify(paymentRepository).save(org.mockito.ArgumentMatchers.argThat((Payment p) ->
                p.getAmount().compareTo(new BigDecimal("60.00")) == 0
                        && Long.valueOf(7L).equals(p.getChargePriceId())
                        && p.getStatus() == com.portalcursos.ng02.model.EPaymentStatus.PENDING
                        && p.getStudent() == student));
        assertNotNull(entity.getDecidedAt());
    }

    @Test
    void quemPediuNaoPodeAprovarOProprioPedido() {
        when(accessService.current()).thenReturn(admin("adm1"));
        when(repository.findById(9L)).thenReturn(Optional.of(pending("adm1")));

        assertThrows(AccessDeniedException.class, () -> service.approve(9L, null));
        verify(paymentRepository, never()).save(any());
    }

    @Test
    void secretariaNaoDecide() {
        when(accessService.current()).thenReturn(secretaria("ana", COURSE));

        assertThrows(AccessDeniedException.class, () -> service.approve(9L, null));
        assertThrows(AccessDeniedException.class, () -> service.reject(9L, new DecisionRequest("Motivo")));
    }

    @Test
    void pedidoJaDecididoNaoPodeSerAprovadoDeNovo() {
        ChargeAmountException entity = pending("ana");
        entity.setStatus(Status.REJECTED);
        when(accessService.current()).thenReturn(admin("adm2"));
        when(repository.findById(9L)).thenReturn(Optional.of(entity));

        assertThrows(BusinessException.class, () -> service.approve(9L, null));
        verify(paymentRepository, never()).save(any());
    }

    @Test
    void rejeitarExigeMotivoENaoEmiteCobranca() {
        ChargeAmountException entity = pending("ana");
        when(accessService.current()).thenReturn(admin("adm2"));

        assertThrows(BusinessException.class, () -> service.reject(9L, new DecisionRequest("  ")));

        when(repository.findById(9L)).thenReturn(Optional.of(entity));
        when(studentRepository.findById(1L)).thenReturn(Optional.of(student));
        ExceptionDto dto = service.reject(9L, new DecisionRequest("Fora da política de bolsas"));

        assertEquals(Status.REJECTED, dto.status());
        assertEquals("Fora da política de bolsas", dto.decisionReason());
        verify(paymentRepository, never()).save(any());
    }

    // --- listar e política ---

    @Test
    void administradorVeTodosEASecretariaSoOsProprios() {
        ChargeAmountException mine = pending("ana");
        when(repository.findAllByOrderByRequestedAtDesc()).thenReturn(List.of(mine));
        when(repository.findByRequestedByOrderByRequestedAtDesc("ana")).thenReturn(List.of(mine));
        when(studentRepository.findById(1L)).thenReturn(Optional.of(student));

        when(accessService.current()).thenReturn(admin("adm1"));
        assertEquals(1, service.list().size());
        verify(repository, never()).findByRequestedByOrderByRequestedAtDesc(any());

        when(accessService.current()).thenReturn(secretaria("ana", COURSE));
        assertEquals(1, service.list().size());
    }

    @Test
    void politicaInformaARegraLigadaEOQueOUsuarioPodeFazer() {
        when(policy.enforceTablePrice()).thenReturn(true);

        when(accessService.current()).thenReturn(admin("adm1"));
        assertEquals(new com.portalcursos.ng02.dto.ChargeExceptionDtos.PolicyDto(true, true, true), service.policy());

        when(accessService.current()).thenReturn(secretaria("ana", COURSE));
        assertEquals(new com.portalcursos.ng02.dto.ChargeExceptionDtos.PolicyDto(true, true, false), service.policy());

        when(accessService.current()).thenReturn(new Access(Level.NONE, Set.of(), Set.of(), "fin"));
        assertEquals(new com.portalcursos.ng02.dto.ChargeExceptionDtos.PolicyDto(true, false, false), service.policy());
    }
}
