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
@Table(name = "students")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@SQLDelete(sql = "UPDATE students SET active = false WHERE id = ?")
@Where(clause = "active = true")
public class Student {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Column(unique = true)
    private String registrationNumber;

    @NotBlank
    private String fullName;

    @NotBlank
    private String email;

    @NotBlank
    private String cpf;

    private String phone;
    private String dateOfBirth;
    private String address;

    @NotBlank
    private String currentCourse;

    @Builder.Default
    private String enrollmentStatus = "PENDENTE";

    // Novos Campos para Matrícula Robusta
    private String nacionalidade;
    private String estadoCivil;
    private String sexo;
    private String numeroReservista;
    private String tituloEleitor;
    private boolean isEstrangeiro;

    @Enumerated(EnumType.STRING)
    private EIngressMethod formaIngresso;

    @Enumerated(EnumType.STRING)
    private EQuotaType tipoCota;

    @OneToMany(mappedBy = "student", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private java.util.List<StudentDocument> documents = new java.util.ArrayList<>();

    @Column(name = "foto_url")
    private String fotoUrl;

    private String creatorName;
    private String creatorPosition;
    
    @Column(columnDefinition = "TEXT")
    private String creatorPhotoUrl;

    @Builder.Default
    private boolean active = true;

    @OneToOne(cascade = CascadeType.ALL)
    @JoinColumn(name = "user_id", referencedColumnName = "id")
    private User user;
}
