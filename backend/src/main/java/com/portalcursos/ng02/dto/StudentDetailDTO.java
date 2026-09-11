package com.portalcursos.ng02.dto;

import com.portalcursos.ng02.model.PostgradStudent;
import com.portalcursos.ng02.model.StaffMember;
import com.portalcursos.ng02.model.Student;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Resposta detalhada de um aluno (GET /{id}, retorno de enroll/update).
 * Substitui a serialização da entidade JPA crua (achado 1.2 da auditoria):
 * não expõe mais {@code course}/{@code user}/{@code creator} como entidades,
 * nem os campos internos de auditoria ({@code version}, {@code lastModifiedBy},
 * {@code active}...). Os nomes de campo espelham o que o frontend já consome.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StudentDetailDTO {
    private Long id;
    private String fullName;
    private String registrationNumber;
    private String email;
    private String phone;
    private String cpf;
    private String dateOfBirth;
    private String address;
    private String currentCourse;
    private String enrollmentStatus;

    private String nacionalidade;
    private String estadoCivil;
    private String sexo;
    private String numeroReservista;
    private String tituloEleitor;
    private boolean estrangeiro;

    private String formaIngresso;
    private String tipoCota;

    private String fotoMatricula;
    private LocalDateTime registrationDate;

    private List<StudentDocumentDTO> documents;

    private String creatorName;
    private String creatorPosition;
    private String creatorPhotoUrl;

    // Só preenchidos para PostgradStudent
    private String graduationInstitution;
    private Integer graduationYear;
    private String desiredCourse;

    public static StudentDetailDTO from(Student s) {
        if (s == null) return null;
        StudentDetailDTO dto = StudentDetailDTO.builder()
                .id(s.getId())
                .fullName(s.getFullName())
                .registrationNumber(s.getRegistrationNumber())
                .email(s.getEmail())
                .phone(s.getPhone())
                .cpf(s.getCpf())
                .dateOfBirth(s.getDateOfBirth())
                .address(s.getAddress())
                .currentCourse(s.getCourse() != null ? s.getCourse().getDenominacaoCurso() : null)
                .enrollmentStatus(s.getEnrollmentStatus())
                .nacionalidade(s.getNacionalidade())
                .estadoCivil(s.getEstadoCivil())
                .sexo(s.getSexo())
                .numeroReservista(s.getNumeroReservista())
                .tituloEleitor(s.getTituloEleitor())
                .estrangeiro(s.isEstrangeiro())
                .formaIngresso(s.getFormaIngresso() != null ? s.getFormaIngresso().name() : null)
                .tipoCota(s.getTipoCota() != null ? s.getTipoCota().name() : null)
                .fotoMatricula(s.getFotoMatricula())
                .registrationDate(s.getRegistrationDate())
                .documents(s.getDocuments() != null
                        ? s.getDocuments().stream().map(StudentDocumentDTO::from).collect(Collectors.toList())
                        : List.of())
                .build();

        StaffMember creator = s.getCreator();
        if (creator != null) {
            dto.setCreatorName(creator.getFullName());
            dto.setCreatorPosition(creator.getPosition());
            dto.setCreatorPhotoUrl(creator.getFotoUrl());
        }

        if (s instanceof PostgradStudent pg) {
            dto.setGraduationInstitution(pg.getGraduationInstitution());
            dto.setGraduationYear(pg.getGraduationYear());
            dto.setDesiredCourse(pg.getDesiredCourse());
        }
        return dto;
    }
}
