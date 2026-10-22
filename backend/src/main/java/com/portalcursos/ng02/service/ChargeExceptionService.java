package com.portalcursos.ng02.service;

import com.portalcursos.ng02.dto.ChargeExceptionDtos.DecisionRequest;
import com.portalcursos.ng02.dto.ChargeExceptionDtos.ExceptionDto;
import com.portalcursos.ng02.dto.ChargeExceptionDtos.ExceptionRequest;
import com.portalcursos.ng02.dto.ChargeExceptionDtos.PolicyDto;
import com.portalcursos.ng02.exception.BusinessException;
import com.portalcursos.ng02.exception.ResourceNotFoundException;
import com.portalcursos.ng02.model.ChargeAmountException;
import com.portalcursos.ng02.model.ChargeAmountException.Status;
import com.portalcursos.ng02.model.ChargePrice;
import com.portalcursos.ng02.model.EAcademicLevel;
import com.portalcursos.ng02.model.EPaymentStatus;
import com.portalcursos.ng02.model.Payment;
import com.portalcursos.ng02.model.Student;
import com.portalcursos.ng02.repository.ChargeAmountExceptionRepository;
import com.portalcursos.ng02.repository.ChargePriceRepository;
import com.portalcursos.ng02.repository.PaymentRepository;
import com.portalcursos.ng02.repository.PostgradStudentRepository;
import com.portalcursos.ng02.repository.StudentRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Exceção controlada à regra "o valor vem da tabela": a secretária do curso do aluno (ou um administrador)
 * pede um valor diferente com justificativa; nada é cobrado até um administrador, que não seja quem pediu,
 * aprovar. Aprovado, o servidor emite a cobrança com o valor autorizado, na mesma transação da decisão.
 */
@Service
@RequiredArgsConstructor
public class ChargeExceptionService {

    private static final Logger logger = LoggerFactory.getLogger(ChargeExceptionService.class);
    private static final int MIN_REASON_LENGTH = 10;

    private final ChargeAmountExceptionRepository repository;
    private final StudentRepository studentRepository;
    private final PostgradStudentRepository postgradStudentRepository;
    private final PaymentRepository paymentRepository;
    private final ChargeCatalogService catalogService;
    private final ChargePriceRepository priceRepository;
    private final ChargePriceAccessService accessService;
    private final ChargePolicy policy;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    public PolicyDto policy() {
        ChargePriceAccessService.Access access = accessService.current();
        return new PolicyDto(policy.enforceTablePrice(), access.hasAccess(), access.isAdmin());
    }

    @Transactional
    public ExceptionDto request(ExceptionRequest request) {
        validate(request);
        ChargePriceAccessService.Access access = accessService.current();
        if (!access.hasAccess()) {
            throw new AccessDeniedException("Somente a secretária do curso ou um administrador pode pedir um valor diferente do da tabela");
        }
        Student student = loadStudent(request.studentId(), request.academicLevel());
        ChargeCatalogService.Resolved table =
                catalogService.resolve(request.chargePriceId(), student, request.academicLevel());

        if (!access.isAdmin()) {
            UUID courseId = catalogService.courseIdOf(student);
            if (courseId == null || !access.courseIds().contains(courseId)) {
                throw new AccessDeniedException("Você não atende o curso deste aluno");
            }
        }
        if (request.requestedAmount().compareTo(table.value()) == 0) {
            throw new BusinessException("O valor pedido é igual ao da tabela; lance a cobrança normalmente");
        }
        if (repository.existsByStudentIdAndChargePriceIdAndStatus(student.getId(), table.price().getId(), Status.PENDING)) {
            throw new BusinessException("Já existe um pedido pendente para este aluno e procedimento");
        }

        ChargeAmountException entity = new ChargeAmountException();
        entity.setStudentId(student.getId());
        entity.setAcademicLevel(request.academicLevel());
        entity.setChargePriceId(table.price().getId());
        entity.setTableAmount(table.value());
        entity.setRequestedAmount(request.requestedAmount());
        entity.setDueDate(request.dueDate());
        entity.setDescription(blankToNull(request.description()));
        entity.setReason(request.reason().trim());
        entity.setRequestedBy(access.actor());
        ChargeAmountException saved = repository.save(entity);
        logger.info("[CHARGE-EXCEPTION] pedido {} criado por {} (aluno={}, tabela={}, pedido={})",
                saved.getId(), access.actor(), student.getId(), table.value(), request.requestedAmount());
        return toDto(saved, student, table.price().getName());
    }

    @Transactional
    public ExceptionDto approve(Long id, DecisionRequest decision) {
        ChargePriceAccessService.Access access = requireAdmin();
        ChargeAmountException entity = pending(id);
        if (access.actor().equals(entity.getRequestedBy())) {
            throw new AccessDeniedException("Quem pediu a exceção não pode aprová-la");
        }
        Student student = loadStudent(entity.getStudentId(), entity.getAcademicLevel());
        ChargeCatalogService.Resolved table =
                catalogService.resolve(entity.getChargePriceId(), student, entity.getAcademicLevel());

        Payment payment = Payment.builder()
                .amount(entity.getRequestedAmount())
                .dueDate(entity.getDueDate())
                .status(EPaymentStatus.PENDING)
                .category(table.category())
                .secretaryProcessType(table.processType())
                .academicLevel(entity.getAcademicLevel())
                .description(entity.getDescription() != null ? entity.getDescription() : table.price().getName())
                .chargePriceId(table.price().getId())
                .build();
        payment.setStudent(student);
        payment.setStudentPhotoUrl(student.getFotoMatricula());
        payment.settleIfFree();
        auditService.injectCreator(payment);
        Payment issued = paymentRepository.save(payment);

        entity.setStatus(Status.APPROVED);
        entity.setDecidedBy(access.actor());
        entity.setDecidedAt(LocalDateTime.now());
        entity.setDecisionReason(blankToNull(decision == null ? null : decision.reason()));
        entity.setPaymentId(issued.getId());
        repository.save(entity);
        logger.info("[CHARGE-EXCEPTION] pedido {} aprovado por {}; cobrança {} emitida", id, access.actor(), issued.getId());
        return toDto(entity, student, table.price().getName());
    }

    @Transactional
    public ExceptionDto reject(Long id, DecisionRequest decision) {
        ChargePriceAccessService.Access access = requireAdmin();
        if (decision == null || decision.reason() == null || decision.reason().isBlank()) {
            throw new BusinessException("Informe o motivo da rejeição");
        }
        ChargeAmountException entity = pending(id);
        if (access.actor().equals(entity.getRequestedBy())) {
            throw new AccessDeniedException("Quem pediu a exceção não pode decidi-la");
        }
        entity.setStatus(Status.REJECTED);
        entity.setDecidedBy(access.actor());
        entity.setDecidedAt(LocalDateTime.now());
        entity.setDecisionReason(decision.reason().trim());
        repository.save(entity);
        logger.info("[CHARGE-EXCEPTION] pedido {} rejeitado por {}", id, access.actor());
        Student student = loadStudent(entity.getStudentId(), entity.getAcademicLevel());
        String procedureName = priceRepository.findById(entity.getChargePriceId()).map(ChargePrice::getName).orElse(null);
        return toDto(entity, student, procedureName);
    }

    /** Administrador vê todos os pedidos; a secretária vê só os que ela fez. */
    @Transactional(readOnly = true)
    public List<ExceptionDto> list() {
        ChargePriceAccessService.Access access = accessService.current();
        if (!access.hasAccess()) {
            throw new AccessDeniedException("Sem acesso aos pedidos de exceção");
        }
        List<ChargeAmountException> rows = access.isAdmin()
                ? repository.findAllByOrderByRequestedAtDesc()
                : repository.findByRequestedByOrderByRequestedAtDesc(access.actor());
        // Uma consulta só para os nomes dos procedimentos, em vez de uma por pedido.
        Map<Long, String> names = priceRepository.findAllById(
                        rows.stream().map(ChargeAmountException::getChargePriceId).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(ChargePrice::getId, ChargePrice::getName, (a, b) -> a));
        return rows.stream()
                .map(row -> toDto(row, loadStudentOrNull(row.getStudentId(), row.getAcademicLevel()),
                        names.get(row.getChargePriceId())))
                .toList();
    }

    private ChargePriceAccessService.Access requireAdmin() {
        ChargePriceAccessService.Access access = accessService.current();
        if (!access.isAdmin()) {
            throw new AccessDeniedException("Somente um administrador decide os pedidos de exceção");
        }
        return access;
    }

    private ChargeAmountException pending(Long id) {
        ChargeAmountException entity = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Pedido de exceção não encontrado"));
        if (entity.getStatus() != Status.PENDING) {
            throw new BusinessException("Este pedido já foi decidido");
        }
        return entity;
    }

    private static void validate(ExceptionRequest request) {
        if (request == null || request.studentId() == null || request.academicLevel() == null
                || request.chargePriceId() == null || request.dueDate() == null) {
            throw new BusinessException("Informe aluno, nível acadêmico, procedimento e vencimento");
        }
        if (request.requestedAmount() == null || request.requestedAmount().compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessException("Informe um valor pedido válido (zero ou maior)");
        }
        if (request.reason() == null || request.reason().trim().length() < MIN_REASON_LENGTH) {
            throw new BusinessException("Informe a justificativa (mínimo de " + MIN_REASON_LENGTH + " caracteres)");
        }
    }

    private Student loadStudent(Long studentId, EAcademicLevel level) {
        return level == EAcademicLevel.GRADUATION
                ? studentRepository.findById(studentId)
                        .orElseThrow(() -> new ResourceNotFoundException("Estudante de graduação não encontrado"))
                : postgradStudentRepository.findById(studentId)
                        .orElseThrow(() -> new ResourceNotFoundException("Estudante de pós-graduação não encontrado"));
    }

    private Student loadStudentOrNull(Long studentId, EAcademicLevel level) {
        return level == EAcademicLevel.GRADUATION
                ? studentRepository.findById(studentId).orElse(null)
                : postgradStudentRepository.findById(studentId).orElse(null);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static ExceptionDto toDto(ChargeAmountException e, Student student, String procedureName) {
        return new ExceptionDto(e.getId(), e.getStudentId(), student == null ? null : student.getFullName(),
                e.getAcademicLevel(), e.getChargePriceId(), procedureName, e.getTableAmount(), e.getRequestedAmount(),
                e.getDueDate(), e.getDescription(), e.getReason(), e.getStatus(), e.getRequestedBy(), e.getRequestedAt(),
                e.getDecidedBy(), e.getDecidedAt(), e.getDecisionReason(), e.getPaymentId());
    }
}
