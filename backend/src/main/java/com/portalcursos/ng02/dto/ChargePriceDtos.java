package com.portalcursos.ng02.dto;

import com.portalcursos.ng02.model.ChargePrice;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/** Contratos da API da tabela de valores das cobranças. */
public final class ChargePriceDtos {

    private ChargePriceDtos() {
    }

    /** Proposta pendente de uma linha, se houver. */
    public record PendingDto(Long id, BigDecimal value, String proposedBy, LocalDateTime proposedAt, String reason) {
    }

    /** Uma linha da tabela, já com o que o perfil atual pode fazer nela. */
    public record RowDto(Long id, String code, String category, String name, ChargePrice.Scope academicScope,
                         UUID courseId, BigDecimal currentValue, Long version,
                         boolean canEdit, boolean canPropose, PendingDto pending) {
    }

    /** Perfil efetivo de quem consulta: ADMIN, HEAD (chefe), SECRETARIA (somente leitura). */
    public record TableDto(String role, List<RowDto> rows) {
    }

    public record SetValueRequest(BigDecimal value, Long version) {
    }

    public record ProposeRequest(BigDecimal value, String reason) {
    }

    public record DecisionRequest(String reason) {
    }

    public record CourseRowRequest(Long sourceId, UUID courseId, BigDecimal value) {
    }

    public record HistoryDto(Long id, BigDecimal proposedValue, String status, String proposedBy,
                             LocalDateTime proposedAt, String reason, String decidedBy,
                             LocalDateTime decidedAt, String decisionReason) {
    }

    public record ScopeRequest(Long staffId, UUID courseId, Boolean head) {
    }

    public record ScopeDto(Long id, Long staffId, UUID courseId, boolean head) {
    }
}
