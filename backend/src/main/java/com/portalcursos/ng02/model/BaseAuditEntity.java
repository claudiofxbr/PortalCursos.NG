package com.portalcursos.ng02.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import lombok.Builder;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
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

    @JsonProperty("creatorName")
    public String getCreatorName() {
        return creator != null ? creator.getFullName() : null;
    }

    @JsonProperty("creatorPosition")
    public String getCreatorPosition() {
        return creator != null ? creator.getPosition() : null;
    }

    @JsonProperty("creatorPhotoUrl")
    public String getCreatorPhotoUrl() {
        return creator != null ? creator.getFotoUrl() : null;
    }

    @Builder.Default
    @Column(nullable = false)
    protected boolean active = true;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "creator_id")
    protected StaffMember creator;

    @CreatedDate
    @Column(name = "created_at", updatable = false)
    protected LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    protected LocalDateTime updatedAt;

    @LastModifiedBy
    @Column(name = "last_modified_by")
    protected String lastModifiedBy;

    @Column(name = "registration_date")
    protected LocalDateTime registrationDate;

    @Version
    protected Long version;

    @PrePersist
    protected void onCreate() {
        if (this.registrationDate == null) {
            this.registrationDate = LocalDateTime.now();
        }
    }
}
