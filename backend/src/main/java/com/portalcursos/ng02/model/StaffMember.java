package com.portalcursos.ng02.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.Where;

@Entity
@Table(name = "staff_members")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@SQLDelete(sql = "UPDATE staff_members SET active = false WHERE id = ?")
@Where(clause = "active = true")
public class StaffMember {
    @Id
    private Long id;

    @NotBlank
    private String fullName;

    @NotBlank
    private String position;

    @NotBlank
    private String department;

    @Column(name = "foto_url")
    private String fotoUrl;

    private String creatorName;
    private String creatorPosition;
    
    @Column(columnDefinition = "TEXT")
    private String creatorPhotoUrl;

    @Builder.Default
    private boolean active = true;

    @OneToOne
    @MapsId
    @JoinColumn(name = "id")
    private User user;
}
