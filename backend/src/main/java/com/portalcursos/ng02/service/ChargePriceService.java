package com.portalcursos.ng02.service;

import com.portalcursos.ng02.dto.ChargePriceDtos.*;
import com.portalcursos.ng02.exception.BusinessException;
import com.portalcursos.ng02.exception.ResourceNotFoundException;
import com.portalcursos.ng02.model.ChargePrice;
import com.portalcursos.ng02.model.ChargePriceChange;
import com.portalcursos.ng02.model.ChargePriceChange.Status;
import com.portalcursos.ng02.model.Course;
import com.portalcursos.ng02.model.StaffCourseScope;
import com.portalcursos.ng02.repository.ChargePriceChangeRepository;
import com.portalcursos.ng02.repository.ChargePriceRepository;
import com.portalcursos.ng02.repository.CourseRepository;
import com.portalcursos.ng02.repository.StaffCourseScopeRepository;
import com.portalcursos.ng02.repository.StaffMemberRepository;
import com.portalcursos.ng02.service.ChargePriceAccessService.Access;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Tabela de valores das cobranças. Regras: administrador altera direto e é o único que confirma; chefe de
 * secretaria só propõe (valor só vale após confirmação); secretaria só lê. O escopo é aplicado aqui, no
 * servidor, nunca pela tela. Faturas já emitidas não são afetadas.
 */
@Service
@RequiredArgsConstructor
public class ChargePriceService {

    private static final Logger logger = LoggerFactory.getLogger(ChargePriceService.class);
    private static final BigDecimal MAX_VALUE = new BigDecimal("9999999.99");
    private static final String DIRECT_CHANGE_REASON = "Alteração direta do administrador";

    private final ChargePriceRepository priceRepository;
    private final ChargePriceChangeRepository changeRepository;
    private final StaffCourseScopeRepository scopeRepository;
    private final CourseRepository courseRepository;
    private final StaffMemberRepository staffRepository;
    private final ChargePriceAccessService accessService;

    // ------------------------------------------------------------------ leitura

    @Transactional(readOnly = true)
    public TableDto list() {
        Access access = requireAccess();
        List<ChargePrice> prices = access.isAdmin()
                ? priceRepository.findAllActive()
                : priceRepository.findVisibleTo(access.courseIds());
        Map<Long, ChargePriceChange> pending = pendingByPrice(prices.stream().map(ChargePrice::getId).toList());
        Map<UUID, String> courseNames = courseNames(prices);
        List<RowDto> rows = prices.stream()
                .filter(access::canSee)
                .map(p -> toRow(p, access, pending.get(p.getId()), courseNames))
                .toList();
        return new TableDto(access.level().name(), rows);
    }

    @Transactional(readOnly = true)
    public List<HistoryDto> history(Long priceId) {
        Access access = requireAccess();
        ChargePrice price = findVisible(priceId, access);
        return changeRepository.findByChargePriceIdOrderByProposedAtDesc(price.getId()).stream()
                .map(c -> new HistoryDto(c.getId(), c.getProposedValue(), c.getStatus().name(), c.getProposedBy(),
                        c.getProposedAt(), c.getReason(), c.getDecidedBy(), c.getDecidedAt(), c.getDecisionReason()))
                .toList();
    }

    // ------------------------------------------------------------------ administrador

    /** Alteração direta pelo administrador. Uma proposta pendente da linha fica superada (rejeitada). */
    @Transactional
    public RowDto setValue(Long priceId, SetValueRequest request) {
        Access access = requireAdmin();
        if (request == null || request.version() == null) {
            throw new BusinessException("Informe a versão da linha (recarregue a tabela).");
        }
        BigDecimal value = normalize(request.value());
        ChargePrice price = priceRepository.findActiveById(priceId)
                .orElseThrow(() -> new ResourceNotFoundException("Cobrança não encontrada"));
        if (!Objects.equals(price.getVersion(), request.version())) {
            throw new BusinessException("Este valor foi alterado por outra pessoa. Recarregue a tabela.");
        }
        price.setCurrentValue(value);
        stamp(price, access);
        // saveAndFlush: a versão (@Version) só é incrementada no flush; sem ele a resposta traria a versão
        // antiga e a edição seguinte, feita com ela, daria falso conflito.
        priceRepository.saveAndFlush(price);

        changeRepository.findFirstByChargePriceIdAndStatus(priceId, Status.PENDING).ifPresent(c -> {
            decide(c, Status.REJECTED, access, "Superada por alteração direta do administrador");
            changeRepository.save(c);
        });
        recordDirectChange(price, value, access, DIRECT_CHANGE_REASON);
        logger.info("[PRICES] valor alterado pelo administrador: priceId={} por={}", priceId, access.actor());
        return toRow(price, access, null, courseNames(List.of(price)));
    }

    /** Cria o valor de um curso a partir de uma linha global (ou de outro curso). */
    @Transactional
    public RowDto createCourseRow(CourseRowRequest request) {
        Access access = requireAdmin();
        if (request == null || request.sourceId() == null || request.courseId() == null) {
            throw new BusinessException("Informe a cobrança de origem e o curso.");
        }
        ChargePrice source = priceRepository.findActiveById(request.sourceId())
                .orElseThrow(() -> new ResourceNotFoundException("Cobrança de origem não encontrada"));
        if (!courseRepository.existsById(request.courseId())) {
            throw new ResourceNotFoundException("Curso não encontrado");
        }
        if (priceRepository.existsByCodeAndCourseIdAndActiveTrue(source.getCode(), request.courseId())) {
            throw new BusinessException("Já existe valor desta cobrança para o curso.");
        }
        ChargePrice row = new ChargePrice();
        row.setCode(source.getCode());
        row.setCategory(source.getCategory());
        row.setName(source.getName());
        row.setAcademicScope(source.getAcademicScope());
        row.setCourseId(request.courseId());
        row.setCurrentValue(request.value() == null ? null : normalize(request.value()));
        stamp(row, access);
        ChargePrice saved = priceRepository.save(row);
        if (saved.getCurrentValue() != null) {
            recordDirectChange(saved, saved.getCurrentValue(), access, "Valor inicial definido pelo administrador");
        }
        logger.info("[PRICES] valor por curso criado: priceId={} por={}", saved.getId(), access.actor());
        return toRow(saved, access, null, courseNames(List.of(saved)));
    }

    @Transactional
    public RowDto approve(Long changeId) {
        Access access = requireAdmin();
        ChargePriceChange change = lockPending(changeId);
        ChargePrice price = priceRepository.findActiveById(change.getChargePriceId())
                .orElseThrow(() -> new ResourceNotFoundException("Cobrança não encontrada"));
        price.setCurrentValue(change.getProposedValue());
        stamp(price, access);
        priceRepository.saveAndFlush(price);
        decide(change, Status.APPROVED, access, null);
        changeRepository.save(change);
        logger.info("[PRICES] proposta confirmada: changeId={} priceId={} por={}", changeId, price.getId(), access.actor());
        return toRow(price, access, null, courseNames(List.of(price)));
    }

    @Transactional
    public RowDto reject(Long changeId, DecisionRequest request) {
        Access access = requireAdmin();
        ChargePriceChange change = lockPending(changeId);
        ChargePrice price = priceRepository.findActiveById(change.getChargePriceId())
                .orElseThrow(() -> new ResourceNotFoundException("Cobrança não encontrada"));
        decide(change, Status.REJECTED, access, request == null ? null : trim(request.reason()));
        changeRepository.save(change);
        logger.info("[PRICES] proposta rejeitada: changeId={} priceId={} por={}", changeId, price.getId(), access.actor());
        return toRow(price, access, null, courseNames(List.of(price)));
    }

    // ------------------------------------------------------------------ chefe de secretaria

    @Transactional
    public RowDto propose(Long priceId, ProposeRequest request) {
        Access access = requireAccess();
        ChargePrice price = findVisible(priceId, access);
        if (!access.canPropose(price)) {
            throw new AccessDeniedException("Você não pode propor valor para esta cobrança");
        }
        BigDecimal value = normalize(request == null ? null : request.value());
        if (price.getCurrentValue() != null && price.getCurrentValue().compareTo(value) == 0) {
            throw new BusinessException("O valor proposto é igual ao valor vigente.");
        }
        if (changeRepository.findFirstByChargePriceIdAndStatus(priceId, Status.PENDING).isPresent()) {
            throw new BusinessException("Já existe uma proposta aguardando confirmação para esta cobrança.");
        }
        ChargePriceChange change = new ChargePriceChange();
        change.setChargePriceId(priceId);
        change.setProposedValue(value);
        change.setProposedBy(access.actor());
        change.setReason(trim(request.reason()));
        ChargePriceChange saved = changeRepository.save(change);
        logger.info("[PRICES] proposta criada: changeId={} priceId={} por={}", saved.getId(), priceId, access.actor());
        return toRow(price, access, saved, courseNames(List.of(price)));
    }

    // ------------------------------------------------------------------ escopo (administrador)

    @Transactional(readOnly = true)
    public List<ScopeDto> listScopes() {
        requireAdmin();
        return scopeRepository.findAll().stream().map(ChargePriceService::toScope).toList();
    }

    @Transactional
    public ScopeDto saveScope(ScopeRequest request) {
        Access access = requireAdmin();
        if (request == null || request.staffId() == null || request.courseId() == null) {
            throw new BusinessException("Informe o colaborador e o curso.");
        }
        if (!staffRepository.existsById(request.staffId())) {
            throw new ResourceNotFoundException("Colaborador não encontrado");
        }
        if (!courseRepository.existsById(request.courseId())) {
            throw new ResourceNotFoundException("Curso não encontrado");
        }
        StaffCourseScope scope = scopeRepository.findByStaffIdAndCourseId(request.staffId(), request.courseId())
                .orElseGet(StaffCourseScope::new);
        scope.setStaffId(request.staffId());
        scope.setCourseId(request.courseId());
        scope.setHead(Boolean.TRUE.equals(request.head()));
        StaffCourseScope saved = scopeRepository.save(scope);
        logger.info("[PRICES] escopo salvo: staffId={} head={} por={}", saved.getStaffId(), saved.isHead(), access.actor());
        return toScope(saved);
    }

    @Transactional
    public void deleteScope(Long scopeId) {
        requireAdmin();
        if (!scopeRepository.existsById(scopeId)) {
            throw new ResourceNotFoundException("Vínculo não encontrado");
        }
        scopeRepository.deleteById(scopeId);
    }

    // ------------------------------------------------------------------ internos

    private Access requireAccess() {
        Access access = accessService.current();
        if (!access.hasAccess()) {
            throw new AccessDeniedException("Sem acesso à tabela de valores");
        }
        return access;
    }

    private Access requireAdmin() {
        Access access = accessService.current();
        if (!access.isAdmin()) {
            throw new AccessDeniedException("Somente administradores podem fazer esta operação");
        }
        return access;
    }

    /** Linha inexistente ou fora do escopo vira 404, para não revelar o que existe em outros cursos. */
    private ChargePrice findVisible(Long priceId, Access access) {
        return priceRepository.findActiveById(priceId)
                .filter(access::canSee)
                .orElseThrow(() -> new ResourceNotFoundException("Cobrança não encontrada"));
    }

    private ChargePriceChange lockPending(Long changeId) {
        ChargePriceChange change = changeRepository.lockById(changeId)
                .orElseThrow(() -> new ResourceNotFoundException("Proposta não encontrada"));
        if (change.getStatus() != Status.PENDING) {
            throw new BusinessException("Esta proposta já foi decidida.");
        }
        return change;
    }

    private Map<Long, ChargePriceChange> pendingByPrice(Collection<Long> priceIds) {
        if (priceIds.isEmpty()) return Map.of();
        return changeRepository.findByChargePriceIdInAndStatus(priceIds, Status.PENDING).stream()
                .collect(Collectors.toMap(ChargePriceChange::getChargePriceId, c -> c, (a, b) -> a));
    }

    /**
     * Registra no histórico uma definição de valor feita direto pelo administrador (já vigente): sem isso só as
     * propostas apareciam, e a linha parecia nunca ter mudado.
     */
    private void recordDirectChange(ChargePrice price, BigDecimal value, Access access, String reason) {
        ChargePriceChange entry = new ChargePriceChange();
        entry.setChargePriceId(price.getId());
        entry.setProposedValue(value);
        entry.setProposedBy(access.actor());
        entry.setReason(reason);
        decide(entry, Status.APPROVED, access, null);
        changeRepository.save(entry);
    }

    private static void decide(ChargePriceChange change, Status status, Access access, String reason) {
        change.setStatus(status);
        change.setDecidedBy(access.actor());
        change.setDecidedAt(LocalDateTime.now());
        change.setDecisionReason(reason);
    }

    private static void stamp(ChargePrice price, Access access) {
        price.setUpdatedAt(LocalDateTime.now());
        price.setUpdatedBy(access.actor());
    }

    /** Nomes dos cursos das linhas (uma consulta só), para a tela mostrar o nome em vez do identificador. */
    private Map<UUID, String> courseNames(Collection<ChargePrice> prices) {
        Set<UUID> ids = prices.stream().map(ChargePrice::getCourseId).filter(Objects::nonNull).collect(Collectors.toSet());
        if (ids.isEmpty()) return Map.of();
        return courseRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(Course::getId, c -> c.getDenominacaoCurso() == null ? "" : c.getDenominacaoCurso(), (a, b) -> a));
    }

    private static RowDto toRow(ChargePrice p, Access access, ChargePriceChange pending, Map<UUID, String> courseNames) {
        PendingDto pendingDto = pending == null ? null
                : new PendingDto(pending.getId(), pending.getProposedValue(), pending.getProposedBy(),
                pending.getProposedAt(), pending.getReason());
        return new RowDto(p.getId(), p.getCode(), p.getCategory(), p.getName(), p.getAcademicScope(), p.getCourseId(),
                p.getCourseId() == null ? null : courseNames.get(p.getCourseId()), p.getCurrentValue(), p.getVersion(), access.isAdmin(), access.canPropose(p), pendingDto);
    }

    private static ScopeDto toScope(StaffCourseScope s) {
        return new ScopeDto(s.getId(), s.getStaffId(), s.getCourseId(), s.isHead());
    }

    /** Valor em reais: obrigatório, de 0,00 a 9.999.999,99, no máximo 2 casas decimais. */
    static BigDecimal normalize(BigDecimal value) {
        if (value == null) {
            throw new BusinessException("Informe o valor.");
        }
        if (value.signum() < 0) {
            throw new BusinessException("O valor não pode ser negativo.");
        }
        if (value.stripTrailingZeros().scale() > 2) {
            throw new BusinessException("Use no máximo 2 casas decimais.");
        }
        BigDecimal normalized = value.setScale(2, RoundingMode.UNNECESSARY);
        if (normalized.compareTo(MAX_VALUE) > 0) {
            throw new BusinessException("Valor acima do limite permitido.");
        }
        return normalized;
    }

    private static String trim(String text) {
        if (text == null) return null;
        String t = text.trim();
        if (t.isEmpty()) return null;
        return t.length() > 500 ? t.substring(0, 500) : t;
    }
}
