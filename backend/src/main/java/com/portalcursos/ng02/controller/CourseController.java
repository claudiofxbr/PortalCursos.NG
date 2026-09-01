package com.portalcursos.ng02.controller;

import com.portalcursos.ng02.model.Course;
import com.portalcursos.ng02.service.CourseService;
import com.portalcursos.ng02.service.AuditService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import com.portalcursos.ng02.dto.MessageResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import lombok.RequiredArgsConstructor;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/courses")
@RequiredArgsConstructor
public class CourseController {

    private static final Logger logger = LoggerFactory.getLogger(CourseController.class);

    private final CourseService courseService;
    private final AuditService auditService;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'SECRETARIA', 'ACADEMICO', 'MATRICULA', 'ROOT_MASTER')")
    public ResponseEntity<?> getAllCourses() {
        return ResponseEntity.ok(courseService.getAllCourses());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SECRETARIA', 'ACADEMICO', 'MATRICULA', 'ROOT_MASTER')")
    public ResponseEntity<?> getCourseById(@PathVariable UUID id) {
        return ResponseEntity.ok(courseService.getCourseById(id));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'ACADEMICO', 'ROOT_MASTER')")
    public ResponseEntity<?> createCourse(@RequestBody Course course) {
        auditService.injectCreator(course);
        Course savedCourse = courseService.createCourse(course);
        logger.info("[ACADEMIC-API] [COURSE-CREATE] Curso criado com sucesso: {}", savedCourse.getDenominacaoCurso());
        return ResponseEntity.ok(savedCourse);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'ACADEMICO', 'ROOT_MASTER')")
    public ResponseEntity<?> updateCourse(@PathVariable UUID id, @RequestBody Course courseDetails) {
        auditService.injectCreator(courseDetails);
        Course updatedCourse = courseService.updateCourse(id, courseDetails);
        logger.info("[ACADEMIC-API] [COURSE-UPDATE] Curso ID {} atualizado.", id);
        return ResponseEntity.ok(updatedCourse);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'ROOT_MASTER')")
    public ResponseEntity<?> deleteCourse(@PathVariable UUID id) {
        courseService.deleteCourse(id);
        logger.warn("[ACADEMIC-API] [COURSE-DELETE] Curso ID {} removido.", id);
        return ResponseEntity.ok(new MessageResponse("Curso removido com sucesso."));
    }
}

