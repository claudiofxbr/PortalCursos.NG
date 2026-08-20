package com.portalcursos.ng02.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import org.hibernate.annotations.SQLDelete;

import lombok.experimental.SuperBuilder;
import lombok.EqualsAndHashCode;
import com.fasterxml.jackson.annotation.JsonIgnore;

@Entity
@Table(name = "staff_members")
@Data
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@SQLDelete(sql = "UPDATE staff_members SET active = false WHERE id = ?")
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
    @JsonIgnore
    private User user;
}
