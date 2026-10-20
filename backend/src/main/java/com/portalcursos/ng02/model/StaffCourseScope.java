package com.portalcursos.ng02.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/** Vínculo entre um colaborador e um curso que ele atende; {@code head} marca o chefe da secretaria naquele curso. */
@Entity
@Table(name = "staff_course_scope")
@Getter
@Setter
@NoArgsConstructor
public class StaffCourseScope {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "staff_id", nullable = false)
    private Long staffId;

    @Column(name = "course_id", nullable = false)
    private UUID courseId;

    @Column(name = "is_head", nullable = false)
    private boolean head;
}
