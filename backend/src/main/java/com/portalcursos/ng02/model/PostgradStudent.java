package com.portalcursos.ng02.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;


import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDateTime;

import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.Where;

@Entity
@Table(name = "postgrad_students")
@Data
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@SQLDelete(sql = "UPDATE postgrad_students SET active = false WHERE id = ?")
@Where(clause = "active = true")
public class PostgradStudent extends BaseAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "registration_number", unique = true, length = 20)
    private String registrationNumber;

    @NotBlank
    @Column(name = "full_name", nullable = false)
    private String fullName;

    @NotBlank
    @Email
    @Column(name = "email", nullable = false, unique = true)
    private String email;

    @Column(name = "cpf", nullable = false, unique = true, length = 14)
    private String cpf;

    @Column(name = "phone", length = 20)
    private String phone;

    @Column(name = "date_of_birth")
    private String dateOfBirth;

    @Column(name = "address")
    private String address;

    @NotBlank
    @Column(name = "graduation_institution", nullable = false)
    private String graduationInstitution;

    @Column(name = "graduation_year")
    private Integer graduationYear;

    @NotBlank
    @Column(name = "desired_course", nullable = false)
    private String desiredCourse;

    @Column(name = "enrollment_status")
    private String enrollmentStatus; // PENDENTE, APROVADO, REJEITADO

    // Caminhos dos Arquivos de Documentos (salvo em /uploads/)
    @Column(name = "diploma_file_path")
    private String diplomaFilePath;

    @Column(name = "rg_cpf_file_path")
    private String rgCpfFilePath;

    @Column(name = "proof_of_address_file_path")
    private String proofOfAddressFilePath;

    @Column(name = "academic_transcript_file_path")
    private String academicTranscriptFilePath;

    @Column(name = "foto_matricula")
    private String fotoMatricula;

    @OneToMany(mappedBy = "postgradStudent", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @lombok.Builder.Default
    private java.util.List<Payment> payments = new java.util.ArrayList<>();

    @JsonProperty("registrationDate")
    public LocalDateTime getRegistrationDate() {
        return this.createdAt;
    }

    @Override
    @PrePersist
    protected void onCreate() {
        super.onCreate();
        if (enrollmentStatus == null) {
            enrollmentStatus = "PENDENTE";
        }
    }
}
