package com.portalcursos.ng02.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.portalcursos.ng02.dto.ManualChargeRequest;
import com.portalcursos.ng02.exception.BusinessException;
import com.portalcursos.ng02.exception.ResourceNotFoundException;
import com.portalcursos.ng02.model.ChargePrice;
import com.portalcursos.ng02.model.EPaymentCategory;
import com.portalcursos.ng02.model.ESecretaryProcessType;
import com.portalcursos.ng02.model.EAcademicLevel;
import com.portalcursos.ng02.model.EPaymentStatus;
import com.portalcursos.ng02.model.Payment;
import com.portalcursos.ng02.model.PostgradStudent;
import com.portalcursos.ng02.model.Student;
import com.portalcursos.ng02.repository.PaymentRepository;
import com.portalcursos.ng02.repository.PostgradStudentRepository;
import com.portalcursos.ng02.repository.StudentRepository;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

/**
 * Cobertura do Lote E, item F2: PaymentService extraído de FinancialController
 * (createManualCharge/updateCharge/deleteCharge).
 */
@ExtendWith(MockitoExtension.class)
public class PaymentServiceTest {

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private StudentRepository studentRepository;

    @Mock
    private PostgradStudentRepository postgradStudentRepository;

    @Mock
    private AuditService auditService;

    @Mock
    private ChargeCatalogService chargeCatalogService;

    @Mock
    private ChargePolicy chargePolicy;

    @Mock
    private AdminGuard adminGuard;

    @InjectMocks
    private PaymentService paymentService;

    private ManualChargeRequest requestFor(EAcademicLevel level, Long studentId) {
        ManualChargeRequest request = new ManualChargeRequest();
        request.setAmount(new BigDecimal("250.00"));
        request.setDueDate(LocalDate.now().plusDays(10));
        request.setAcademicLevel(level);
        request.setStudentId(studentId);
        request.setDescription("Mensalidade teste");
        return request;
    }

    @Test
    public void createManualChargeGraduacaoVinculaStudentEFoto() {
        Student student = Student.builder().id(1L).fotoMatricula("foto-grad.jpg").build();
        when(studentRepository.findById(1L)).thenReturn(Optional.of(student));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));

        Payment result = paymentService.createManualCharge(requestFor(EAcademicLevel.GRADUATION, 1L));

        assertEquals(student, result.getStudent());
        assertEquals("foto-grad.jpg", result.getStudentPhotoUrl());
        assertEquals(EPaymentStatus.PENDING, result.getStatus());
        verify(auditService).injectCreator(result);
        verify(postgradStudentRepository, never()).findById(any());
    }

    @Test
    public void createManualChargeGraduacaoComStudentInexistenteLanca404() {
        when(studentRepository.findById(99L)).thenReturn(Optional.empty());

        ResourceNotFoundException ex = assertThrows(ResourceNotFoundException.class,
                () -> paymentService.createManualCharge(requestFor(EAcademicLevel.GRADUATION, 99L)));

        assertEquals("Estudante de graduação não encontrado", ex.getMessage());
        verify(paymentRepository, never()).save(any());
    }

    @Test
    public void createManualChargePosGraduacaoVinculaPostgradStudent() {
        PostgradStudent postgrad = PostgradStudent.builder().id(2L).fotoMatricula("foto-pos.jpg").build();
        when(postgradStudentRepository.findById(2L)).thenReturn(Optional.of(postgrad));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));

        Payment result = paymentService.createManualCharge(requestFor(EAcademicLevel.POSTGRADUATE, 2L));

        assertEquals(postgrad, result.getStudent());
        assertEquals("foto-pos.jpg", result.getStudentPhotoUrl());
        verify(studentRepository, never()).findById(any());
    }

    @Test
    public void createManualChargePosGraduacaoComStudentInexistenteLanca404() {
        when(postgradStudentRepository.findById(88L)).thenReturn(Optional.empty());

        ResourceNotFoundException ex = assertThrows(ResourceNotFoundException.class,
                () -> paymentService.createManualCharge(requestFor(EAcademicLevel.POSTGRADUATE, 88L)));

        assertEquals("Estudante de pós-graduação não encontrado", ex.getMessage());
        verify(paymentRepository, never()).save(any());
    }

    @Test
    public void updateChargeAtualizaCamposEPersiste() {
        Payment existing = Payment.builder().id(5L).amount(new BigDecimal("100.00")).build();
        when(paymentRepository.findByIdWithCreatorAndStudent(5L)).thenReturn(Optional.of(existing));
        when(paymentRepository.save(existing)).thenReturn(existing);

        ManualChargeRequest request = requestFor(EAcademicLevel.GRADUATION, 1L);
        request.setAmount(new BigDecimal("300.00"));

        Payment result = paymentService.updateCharge(5L, request);

        assertEquals(new BigDecimal("300.00"), result.getAmount());
        verify(auditService).injectCreator(existing);
    }

    @Test
    public void updateChargeSemValorNoPedidoMantemOValorDaCobranca() {
        Payment existing = Payment.builder().id(5L).amount(new BigDecimal("100.00")).build();
        when(paymentRepository.findByIdWithCreatorAndStudent(5L)).thenReturn(Optional.of(existing));
        when(paymentRepository.save(existing)).thenReturn(existing);

        ManualChargeRequest request = requestFor(EAcademicLevel.GRADUATION, 1L);
        request.setAmount(null);

        assertEquals(new BigDecimal("100.00"), paymentService.updateCharge(5L, request).getAmount());
    }

    @Test
    public void updateChargeComValorNegativoEhRecusado() {
        Payment existing = Payment.builder().id(5L).amount(new BigDecimal("100.00")).build();
        when(paymentRepository.findByIdWithCreatorAndStudent(5L)).thenReturn(Optional.of(existing));

        ManualChargeRequest request = requestFor(EAcademicLevel.GRADUATION, 1L);
        request.setAmount(new BigDecimal("-1.00"));

        assertThrows(BusinessException.class, () -> paymentService.updateCharge(5L, request));
        assertEquals(new BigDecimal("100.00"), existing.getAmount());
    }

    @Test
    public void updateChargeInexistenteLanca404() {
        when(paymentRepository.findByIdWithCreatorAndStudent(404L)).thenReturn(Optional.empty());

        ResourceNotFoundException ex = assertThrows(ResourceNotFoundException.class,
                () -> paymentService.updateCharge(404L, requestFor(EAcademicLevel.GRADUATION, 1L)));

        assertEquals("Cobrança não encontrada", ex.getMessage());
        verify(paymentRepository, never()).save(any());
    }

    // Achado da auditoria: paymentRepository.delete() usava @SQLDelete que quebrava sempre
    // contra um banco real (entidade versionada, SQL customizado sem o parâmetro de
    // @Version — confirmado via Testcontainers). Removido; deleteCharge agora faz
    // soft-delete explícito (setActive(false) + save()), mesmo padrão de
    // UserService.deleteUser. Este teste, mockado, não teria pego o bug original (mock não
    // executa SQL de verdade) — por isso o achado só apareceu com Testcontainers.
    @Test
    public void deleteChargeDesativaQuandoExiste() {
        Payment existing = Payment.builder().id(7L).build();
        existing.setActive(true);
        when(paymentRepository.findById(7L)).thenReturn(Optional.of(existing));
        when(paymentRepository.save(existing)).thenReturn(existing);

        paymentService.deleteCharge(7L);

        assertFalse(existing.isActive());
        verify(paymentRepository).save(existing);
        verify(paymentRepository, never()).delete(any());
        verify(paymentRepository, never()).deleteById(any());
    }

    @Test
    public void deleteChargeInexistenteLanca404() {
        when(paymentRepository.findById(404L)).thenReturn(Optional.empty());

        ResourceNotFoundException ex = assertThrows(ResourceNotFoundException.class,
                () -> paymentService.deleteCharge(404L));

        assertEquals("Cobrança não encontrada", ex.getMessage());
        verify(paymentRepository, never()).save(any());
    }

    @Test
    public void createManualChargeComProcedimentoUsaOValorDaTabelaEIgnoraOAmountEnviado() {
        Student student = Student.builder().id(1L).fotoMatricula("foto.jpg").build();
        ChargePrice row = new ChargePrice();
        row.setId(7L);
        row.setName("Certidão de nada consta");
        when(studentRepository.findById(1L)).thenReturn(Optional.of(student));
        when(chargeCatalogService.resolve(7L, student, EAcademicLevel.GRADUATION)).thenReturn(
                new ChargeCatalogService.Resolved(row, new BigDecimal("15.50"),
                        com.portalcursos.ng02.dto.ChargeCatalogDtos.ValueSource.GLOBAL,
                        EPaymentCategory.INTERNAL_PROCESS, ESecretaryProcessType.CERTIFICATE));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));
        ManualChargeRequest request = requestFor(EAcademicLevel.GRADUATION, 1L);
        request.setAmount(new BigDecimal("9999.00"));
        request.setDescription(null);
        request.setChargePriceId(7L);

        Payment result = paymentService.createManualCharge(request);

        assertEquals(new BigDecimal("15.50"), result.getAmount());
        assertEquals(7L, result.getChargePriceId());
        assertEquals(EPaymentCategory.INTERNAL_PROCESS, result.getCategory());
        assertEquals(ESecretaryProcessType.CERTIFICATE, result.getSecretaryProcessType());
        assertEquals("Certidão de nada consta", result.getDescription());
    }

    @Test
    public void createManualChargeComProcedimentoPreservaADescricaoInformada() {
        Student student = Student.builder().id(1L).build();
        ChargePrice row = new ChargePrice();
        row.setId(7L);
        row.setName("Nome da tabela");
        when(studentRepository.findById(1L)).thenReturn(Optional.of(student));
        when(chargeCatalogService.resolve(7L, student, EAcademicLevel.GRADUATION)).thenReturn(
                new ChargeCatalogService.Resolved(row, BigDecimal.TEN,
                        com.portalcursos.ng02.dto.ChargeCatalogDtos.ValueSource.GLOBAL,
                        EPaymentCategory.OTHER, ESecretaryProcessType.OTHER));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));
        ManualChargeRequest request = requestFor(EAcademicLevel.GRADUATION, 1L);
        request.setChargePriceId(7L);

        assertEquals("Mensalidade teste", paymentService.createManualCharge(request).getDescription());
    }

    @Test
    public void createManualChargeComProcedimentoInvalidoNaoSalvaNada() {
        Student student = Student.builder().id(1L).build();
        when(studentRepository.findById(1L)).thenReturn(Optional.of(student));
        when(chargeCatalogService.resolve(7L, student, EAcademicLevel.GRADUATION))
                .thenThrow(new BusinessException("Valor ainda não definido"));
        ManualChargeRequest request = requestFor(EAcademicLevel.GRADUATION, 1L);
        request.setChargePriceId(7L);

        assertThrows(BusinessException.class, () -> paymentService.createManualCharge(request));

        verify(paymentRepository, never()).save(any());
    }

    @Test
    public void createManualChargeSemProcedimentoMantemOFluxoAntigoComAmountEnviado() {
        Student student = Student.builder().id(1L).build();
        when(studentRepository.findById(1L)).thenReturn(Optional.of(student));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));

        Payment result = paymentService.createManualCharge(requestFor(EAcademicLevel.GRADUATION, 1L));

        assertEquals(new BigDecimal("250.00"), result.getAmount());
        verify(chargeCatalogService, never()).resolve(any(), any(), any());
    }

    // --- Fatura de valor zero (procedimento gratuito) já nasce quitada ---

    @Test
    public void criarCobrancaPorProcedimentoGratuitoJaNasceQuitada() {
        Student student = Student.builder().id(1L).build();
        ChargePrice row = new ChargePrice();
        row.setId(3L);
        row.setName("Contrato e aceite");
        when(studentRepository.findById(1L)).thenReturn(Optional.of(student));
        when(chargeCatalogService.resolve(3L, student, EAcademicLevel.GRADUATION)).thenReturn(
                new ChargeCatalogService.Resolved(row, BigDecimal.ZERO,
                        com.portalcursos.ng02.dto.ChargeCatalogDtos.ValueSource.GLOBAL,
                        EPaymentCategory.INTERNAL_PROCESS, ESecretaryProcessType.OTHER));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));
        ManualChargeRequest request = requestFor(EAcademicLevel.GRADUATION, 1L);
        request.setChargePriceId(3L);

        Payment result = paymentService.createManualCharge(request);

        assertEquals(0, result.getAmount().signum());
        assertEquals(EPaymentStatus.PAID, result.getStatus());
    }

    @Test
    public void criarCobrancaComValorPositivoContinuaPendente() {
        Student student = Student.builder().id(1L).build();
        when(studentRepository.findById(1L)).thenReturn(Optional.of(student));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));

        assertEquals(EPaymentStatus.PENDING,
                paymentService.createManualCharge(requestFor(EAcademicLevel.GRADUATION, 1L)).getStatus());
    }

    @Test
    public void criarCobrancaManualComValorZeroTambemNasceQuitada() {
        Student student = Student.builder().id(1L).build();
        when(studentRepository.findById(1L)).thenReturn(Optional.of(student));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));
        ManualChargeRequest request = requestFor(EAcademicLevel.GRADUATION, 1L);
        request.setAmount(BigDecimal.ZERO);

        assertEquals(EPaymentStatus.PAID, paymentService.createManualCharge(request).getStatus());
    }

    @Test
    public void editarFaturaPendenteParaValorZeroQuitaAFatura() {
        Payment existing = Payment.builder().id(5L).amount(new BigDecimal("100.00")).status(EPaymentStatus.PENDING).build();
        when(paymentRepository.findByIdWithCreatorAndStudent(5L)).thenReturn(Optional.of(existing));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));
        ManualChargeRequest request = requestFor(EAcademicLevel.GRADUATION, 1L);
        request.setAmount(BigDecimal.ZERO);

        assertEquals(EPaymentStatus.PAID, paymentService.updateCharge(5L, request).getStatus());
    }

    // --- Exclusão e perdão de cobrança só com autorização explícita de administrador ---

    /** Simula um usuário que NÃO é administrador: a guarda recusa qualquer ação protegida. */
    private void actAsNonAdmin() {
        // lenient: nos cenários em que a ação é permitida a guarda nem chega a ser chamada.
        lenient().doThrow(new AccessDeniedException("Somente um administrador pode ..."))
                .when(adminGuard).require(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    public void naoAdministradorNaoExcluiCobrancaENemConsultaOBanco() {
        actAsNonAdmin();

        assertThrows(AccessDeniedException.class, () -> paymentService.deleteCharge(5L));

        verifyNoInteractions(paymentRepository);
    }

    @Test
    public void administradorExcluiECobrancaFicaInativa() {
        Payment existing = Payment.builder().id(5L).amount(new BigDecimal("100.00")).status(EPaymentStatus.PENDING).build();
        when(paymentRepository.findById(5L)).thenReturn(Optional.of(existing));

        paymentService.deleteCharge(5L);

        assertFalse(existing.isActive());
        verify(adminGuard).require(org.mockito.ArgumentMatchers.contains("excluir"));
        verify(paymentRepository).save(existing);
    }

    private Payment emAberto(EPaymentStatus status) {
        Payment existing = Payment.builder().id(5L).amount(new BigDecimal("100.00")).status(status).build();
        lenient().when(paymentRepository.findByIdWithCreatorAndStudent(5L)).thenReturn(Optional.of(existing));
        lenient().when(paymentRepository.save(existing)).thenReturn(existing);
        return existing;
    }

    private ManualChargeRequest comStatus(EPaymentStatus status) {
        ManualChargeRequest request = requestFor(EAcademicLevel.GRADUATION, 1L);
        request.setAmount(new BigDecimal("100.00"));   // o valor não muda: só a situação está em teste
        request.setStatus(status);
        return request;
    }

    @Test
    public void administradorDaBaixaManualEmCobrancaEmAberto() {
        Payment existing = emAberto(EPaymentStatus.OVERDUE);

        assertEquals(EPaymentStatus.PAID, paymentService.updateCharge(5L, comStatus(EPaymentStatus.PAID)).getStatus());
        verify(adminGuard).require(org.mockito.ArgumentMatchers.contains("situação"));
        assertEquals(new BigDecimal("100.00"), existing.getAmount());
    }

    @Test
    public void administradorCancelaCobrancaPendente() {
        emAberto(EPaymentStatus.PENDING);

        assertEquals(EPaymentStatus.CANCELLED, paymentService.updateCharge(5L, comStatus(EPaymentStatus.CANCELLED)).getStatus());
    }

    @Test
    public void naoAdministradorNaoMudaASituacao() {
        actAsNonAdmin();
        Payment existing = emAberto(EPaymentStatus.PENDING);

        assertThrows(AccessDeniedException.class, () -> paymentService.updateCharge(5L, comStatus(EPaymentStatus.PAID)));

        assertEquals(EPaymentStatus.PENDING, existing.getStatus());
        verify(paymentRepository, never()).save(any());
    }

    @Test
    public void situacaoIgualAAtualNaoExigeAdministradorNemMuda() {
        actAsNonAdmin();
        Payment existing = emAberto(EPaymentStatus.PENDING);

        assertEquals(EPaymentStatus.PENDING, paymentService.updateCharge(5L, comStatus(EPaymentStatus.PENDING)).getStatus());
        assertEquals(EPaymentStatus.PENDING, existing.getStatus());
    }

    @Test
    public void pendenteEAtrasadoSaoAutomaticosNaoSePodeEscolher() {
        Payment existing = emAberto(EPaymentStatus.OVERDUE);

        assertThrows(BusinessException.class, () -> paymentService.updateCharge(5L, comStatus(EPaymentStatus.PENDING)));

        assertEquals(EPaymentStatus.OVERDUE, existing.getStatus());
    }

    @Test
    public void cobrancaPagaOuCanceladaNaoVoltaAtras() {
        Payment paga = emAberto(EPaymentStatus.PAID);

        assertThrows(BusinessException.class, () -> paymentService.updateCharge(5L, comStatus(EPaymentStatus.CANCELLED)));

        assertEquals(EPaymentStatus.PAID, paga.getStatus());
    }

    @Test
    public void naoAdministradorNaoAlteraOValorDeCobrancaJaLancada() {
        actAsNonAdmin();
        Payment existing = Payment.builder().id(5L).amount(new BigDecimal("100.00")).status(EPaymentStatus.PENDING).build();
        when(paymentRepository.findByIdWithCreatorAndStudent(5L)).thenReturn(Optional.of(existing));
        ManualChargeRequest request = requestFor(EAcademicLevel.GRADUATION, 1L);
        request.setAmount(new BigDecimal("40.00"));

        assertThrows(AccessDeniedException.class, () -> paymentService.updateCharge(5L, request));

        assertEquals(new BigDecimal("100.00"), existing.getAmount());
        verify(paymentRepository, never()).save(any());
    }

    @Test
    public void naoAdministradorNaoZeraACobrancaParaQuitala() {
        actAsNonAdmin();
        Payment existing = Payment.builder().id(5L).amount(new BigDecimal("100.00")).status(EPaymentStatus.PENDING).build();
        when(paymentRepository.findByIdWithCreatorAndStudent(5L)).thenReturn(Optional.of(existing));
        ManualChargeRequest request = requestFor(EAcademicLevel.GRADUATION, 1L);
        request.setAmount(BigDecimal.ZERO);

        assertThrows(AccessDeniedException.class, () -> paymentService.updateCharge(5L, request));

        assertEquals(EPaymentStatus.PENDING, existing.getStatus());
    }

    @Test
    public void naoAdministradorAindaEditaDescricaoComOMesmoValor() {
        actAsNonAdmin();
        Payment existing = Payment.builder().id(5L).amount(new BigDecimal("100.00")).status(EPaymentStatus.PENDING).build();
        when(paymentRepository.findByIdWithCreatorAndStudent(5L)).thenReturn(Optional.of(existing));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));
        ManualChargeRequest request = requestFor(EAcademicLevel.GRADUATION, 1L);
        request.setAmount(new BigDecimal("100.0"));
        request.setDescription("Só a descrição mudou");

        assertEquals("Só a descrição mudou", paymentService.updateCharge(5L, request).getDescription());
    }

    @Test
    public void naoAdministradorNaoLancaCobrancaManualDeValorZero() {
        actAsNonAdmin();
        ManualChargeRequest request = requestFor(EAcademicLevel.GRADUATION, 1L);
        request.setAmount(BigDecimal.ZERO);

        assertThrows(AccessDeniedException.class, () -> paymentService.createManualCharge(request));

        verify(paymentRepository, never()).save(any());
    }

    @Test
    public void valorManualNegativoEhRecusadoParaTodos() {
        ManualChargeRequest request = requestFor(EAcademicLevel.GRADUATION, 1L);
        request.setAmount(new BigDecimal("-1.00"));

        assertThrows(BusinessException.class, () -> paymentService.createManualCharge(request));

        verify(paymentRepository, never()).save(any());
    }

    @Test
    public void naoAdministradorLancaProcedimentoGratuitoDaTabelaPoisOValorFoiDefinidoPeloAdministrador() {
        actAsNonAdmin();
        Student student = Student.builder().id(1L).build();
        ChargePrice row = new ChargePrice();
        row.setId(3L);
        row.setName("Contrato e aceite");
        when(studentRepository.findById(1L)).thenReturn(Optional.of(student));
        when(chargeCatalogService.resolve(3L, student, EAcademicLevel.GRADUATION)).thenReturn(
                new ChargeCatalogService.Resolved(row, BigDecimal.ZERO,
                        com.portalcursos.ng02.dto.ChargeCatalogDtos.ValueSource.GLOBAL,
                        EPaymentCategory.INTERNAL_PROCESS, ESecretaryProcessType.OTHER));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));
        ManualChargeRequest request = requestFor(EAcademicLevel.GRADUATION, 1L);
        request.setChargePriceId(3L);

        assertEquals(EPaymentStatus.PAID, paymentService.createManualCharge(request).getStatus());
    }

    // --- Regra "o valor vem sempre da tabela" (política ligada) ---

    @Test
    public void comAPoliticaLigadaCriarCobrancaSemProcedimentoELancadoEFalhaSemSalvar() {
        when(chargePolicy.enforceTablePrice()).thenReturn(true);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> paymentService.createManualCharge(requestFor(EAcademicLevel.GRADUATION, 1L)));

        assertTrue(ex.getMessage().contains("tabela de valores"));
        verify(paymentRepository, never()).save(any());
        verify(studentRepository, never()).findById(any());
    }

    @Test
    public void comAPoliticaLigadaCriarCobrancaComProcedimentoContinuaFuncionando() {
        when(chargePolicy.enforceTablePrice()).thenReturn(true);
        Student student = Student.builder().id(1L).build();
        ChargePrice row = new ChargePrice();
        row.setId(7L);
        row.setName("Nome da tabela");
        when(studentRepository.findById(1L)).thenReturn(Optional.of(student));
        when(chargeCatalogService.resolve(7L, student, EAcademicLevel.GRADUATION)).thenReturn(
                new ChargeCatalogService.Resolved(row, new BigDecimal("15.50"),
                        com.portalcursos.ng02.dto.ChargeCatalogDtos.ValueSource.GLOBAL,
                        EPaymentCategory.OTHER, ESecretaryProcessType.OTHER));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));
        ManualChargeRequest request = requestFor(EAcademicLevel.GRADUATION, 1L);
        request.setChargePriceId(7L);

        assertEquals(new BigDecimal("15.50"), paymentService.createManualCharge(request).getAmount());
    }

    @Test
    public void comAPoliticaLigadaEditarOValorDaFaturaEhRecusado() {
        when(chargePolicy.enforceTablePrice()).thenReturn(true);
        Payment existing = Payment.builder().id(5L).amount(new BigDecimal("100.00")).build();
        when(paymentRepository.findByIdWithCreatorAndStudent(5L)).thenReturn(Optional.of(existing));
        ManualChargeRequest request = requestFor(EAcademicLevel.GRADUATION, 1L);
        request.setAmount(new BigDecimal("1.00"));

        assertThrows(BusinessException.class, () -> paymentService.updateCharge(5L, request));

        assertEquals(new BigDecimal("100.00"), existing.getAmount());
        verify(paymentRepository, never()).save(any());
    }

    @Test
    public void comAPoliticaLigadaEditarVencimentoEDescricaoComOMesmoValorFunciona() {
        when(chargePolicy.enforceTablePrice()).thenReturn(true);
        Payment existing = Payment.builder().id(5L).amount(new BigDecimal("100.00")).build();
        when(paymentRepository.findByIdWithCreatorAndStudent(5L)).thenReturn(Optional.of(existing));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));
        ManualChargeRequest request = requestFor(EAcademicLevel.GRADUATION, 1L);
        request.setAmount(new BigDecimal("100.0"));
        request.setDescription("Nova descrição");

        Payment result = paymentService.updateCharge(5L, request);

        assertEquals(new BigDecimal("100.00"), result.getAmount());
        assertEquals("Nova descrição", result.getDescription());
    }

    @Test
    public void comAPoliticaDesligadaEditarOValorContinuaPermitido() {
        Payment existing = Payment.builder().id(5L).amount(new BigDecimal("100.00")).build();
        when(paymentRepository.findByIdWithCreatorAndStudent(5L)).thenReturn(Optional.of(existing));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));
        ManualChargeRequest request = requestFor(EAcademicLevel.GRADUATION, 1L);
        request.setAmount(new BigDecimal("120.00"));

        assertEquals(new BigDecimal("120.00"), paymentService.updateCharge(5L, request).getAmount());
    }
}
