package com.portalcursos.ng02.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

import org.hibernate.annotations.SQLDelete;

@Entity
@Table(name = "repair_tickets")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@SQLDelete(sql = "UPDATE repair_tickets SET active = false WHERE id = ?")
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
    @Column(name = "photo_url", columnDefinition = "TEXT")
    private java.util.List<String> photoUrls = new java.util.ArrayList<>(); // Lista de URLs das evidências fotográficas

    @Column(name = "main_photo_url")
    private String mainPhotoUrl;

    private LocalDateTime createdAt;

    private LocalDateTime resolvedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reported_by_id", referencedColumnName = "id")
    private User reportedBy;

    @Column(name = "reported_by_name")
    private String reportedByName;

    @Column(name = "reported_by_role")
    private String reportedByRole;
    
    @Column(name = "reporter_photo_url", columnDefinition = "TEXT")
    private String reporterPhotoUrl;

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

    @Builder.Default
    private boolean active = true;
}
