package com.portalcursos.ng02.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.time.LocalDateTime;

import org.hibernate.annotations.SQLDelete;

@Entity
@Table(name = "repair_tickets")
@Data
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@SQLDelete(sql = "UPDATE repair_tickets SET active = false WHERE id = ?")
@EqualsAndHashCode(callSuper=true)
public class RepairTicket extends BaseAuditEntity {
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
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "repair_photos", joinColumns = @JoinColumn(name = "repair_ticket_id"))
    @Column(name = "photo_url", columnDefinition = "TEXT")
    private java.util.List<String> photoUrls = new java.util.ArrayList<>(); // Evidências fotográficas

    @Column(name = "main_photo_url")
    private String mainPhotoUrl;

    private LocalDateTime resolvedAt;



    public enum ERepairStatus {
        OPEN, IN_PROGRESS, RESOLVED, CANCELLED
    }

    @PrePersist
    @Override
    protected void onCreate() {
        super.onCreate();
        if (status == null) {
            status = ERepairStatus.OPEN;
        }
    }
}
