package com.portalcursos.ng02.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import lombok.Builder;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import java.time.LocalDateTime;

@MappedSuperclass
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
@EntityListeners(AuditingEntityListener.class)
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

    @CreatedDate
    @Column(name = "created_at", updatable = false)
    protected LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    protected LocalDateTime updatedAt;

    @Column(name = "registration_date")
    protected LocalDateTime registrationDate;

    @Version
    protected Long version;

    @PrePersist
    protected void onCreate() {
        if (this.registrationDate == null) {
            this.registrationDate = LocalDateTime.now();
        }
        // active default is already set by Builder.Default or manually
    }
}
