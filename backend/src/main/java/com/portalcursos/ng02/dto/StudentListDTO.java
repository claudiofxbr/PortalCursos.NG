package com.portalcursos.ng02.dto;

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
 * Resposta de listagem de alunos. Não carrega dateOfBirth, address, nem as
 * entidades aninhadas (course/user/creator) e os campos internos de auditoria
 * (version, lastModifiedBy, active, createdAt/updatedAt, payments) que a
 * serialização da entidade crua expunha — achado 1.2 da auditoria.
 * cpf é mantido porque as telas de listagem (matrícula, financeiro) o usam
 * para identificar o aluno, e os endpoints já são restritos por role.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StudentListDTO {
    private Long id;
    private String fullName;
    private String registrationNumber;
    private String email;
    private String phone;
    private String cpf;
    private String enrollmentStatus;
    private String currentCourse;
    private String fotoMatricula;
    private LocalDateTime registrationDate;

    private List<StudentDocumentDTO> documents;

    private String creatorName;
    private String creatorPosition;
    private String creatorPhotoUrl;

    public static StudentListDTO from(Student s) {
        if (s == null) return null;
        StudentListDTO dto = StudentListDTO.builder()
                .id(s.getId())
                .fullName(s.getFullName())
                .registrationNumber(s.getRegistrationNumber())
                .email(s.getEmail())
                .phone(s.getPhone())
                .cpf(s.getCpf())
                .enrollmentStatus(s.getEnrollmentStatus())
                .currentCourse(s.getCourse() != null ? s.getCourse().getDenominacaoCurso() : null)
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
        return dto;
    }
}
