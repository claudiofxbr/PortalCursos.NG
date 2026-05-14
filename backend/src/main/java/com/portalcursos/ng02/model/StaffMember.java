package com.portalcursos.ng02.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.Where;

import lombok.experimental.SuperBuilder;
import lombok.EqualsAndHashCode;

@Entity
@Table(name = "staff_members")
@Data
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@SQLDelete(sql = "UPDATE staff_members SET active = false WHERE id = ?")
@Where(clause = "active = true")
@EqualsAndHashCode(callSuper=true)
public class StaffMember extends BaseAuditEntity {
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

    @OneToOne
    @MapsId
    @JoinColumn(name = "id")
    private User user;
}
