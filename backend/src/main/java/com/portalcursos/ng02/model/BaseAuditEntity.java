package com.portalcursos.ng02.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import lombok.Builder;
import java.time.LocalDateTime;

@MappedSuperclass
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public abstract class BaseAuditEntity {

    @Builder.Default
    @Column(nullable = false)
    protected boolean active = true;

    @Column(name = "creator_name")
    protected String creatorName;

    @Column(name = "creator_position")
    protected String creatorPosition;
    
    @Column(name = "creator_photo_url", columnDefinition = "TEXT")
    protected String creatorPhotoUrl;

    @Column(name = "created_at", updatable = false)
    protected LocalDateTime createdAt;

    @Column(name = "updated_at")
    protected LocalDateTime updatedAt;

    @Column(name = "registration_date")
    protected LocalDateTime registrationDate;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
        this.registrationDate = LocalDateTime.now();
        if (this.active == false) {
            // Ensure default is true if not set
            this.active = true;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
