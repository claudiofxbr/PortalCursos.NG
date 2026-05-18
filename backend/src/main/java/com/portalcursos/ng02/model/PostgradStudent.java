package com.portalcursos.ng02.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;


import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDateTime;

import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.Where;

@Entity
@DiscriminatorValue("POSTGRAD")
@Data
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@EqualsAndHashCode(callSuper=true)
public class PostgradStudent extends Student {

    @NotBlank
    @Column(name = "graduation_institution")
    private String graduationInstitution;

    @Column(name = "graduation_year")
    private Integer graduationYear;

    @NotBlank
    @Column(name = "desired_course")
    private String desiredCourse;

    @Override
    @PrePersist
    protected void onCreate() {
        super.onCreate();
        if (getEnrollmentStatus() == null) {
            setEnrollmentStatus("PENDENTE");
        }
    }
}

