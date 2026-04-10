package com.portalcursos.ng02.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "repair_tickets")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RepairTicket {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    private String title;

    @NotBlank
    private String description;

    @NotBlank
    private String location; // Ex: Lab 03, Bloco B, Corredor

    @Enumerated(EnumType.STRING)
    private ERepairStatus status;

    @Builder.Default
    @ElementCollection
    @CollectionTable(name = "repair_photos", joinColumns = @JoinColumn(name = "repair_ticket_id"))
    @Column(name = "photo_url")
    private java.util.List<String> photoUrls = new java.util.ArrayList<>(); // Lista de URLs das evidências fotográficas

    private LocalDateTime createdAt;

    private LocalDateTime resolvedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reported_by_id", referencedColumnName = "id")
    private User reportedBy;

    public enum ERepairStatus {
        OPEN, IN_PROGRESS, RESOLVED, CANCELLED
    }

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (status == null) {
            status = ERepairStatus.OPEN;
        }
    }
}
