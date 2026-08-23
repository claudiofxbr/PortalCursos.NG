package com.portalcursos.ng02.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Solicitação de eliminação de dados pelo titular (LGPD art. 18, VI / GDPR art. 17).
 * Fica pendente de análise manual do administrador/secretaria antes de qualquer exclusão
 * definitiva, pois registros acadêmicos e financeiros podem estar sujeitos a prazo legal
 * de guarda que este sistema não determina automaticamente.
 */
@Entity
@Table(name = "data_deletion_requests")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DataDeletionRequest {

    public enum Status { PENDING, APPROVED, REJECTED, COMPLETED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "requested_username", nullable = false)
    private String requestedUsername;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private Status status = Status.PENDING;

    @Column(name = "requested_at", nullable = false)
    private LocalDateTime requestedAt;

    @Column(name = "reviewed_by")
    private String reviewedBy;

    private LocalDateTime reviewedAt;

    @Column(length = 1000)
    private String reviewNotes;

    @PrePersist
    protected void onCreate() {
        if (this.requestedAt == null) {
            this.requestedAt = LocalDateTime.now();
        }
    }
}
