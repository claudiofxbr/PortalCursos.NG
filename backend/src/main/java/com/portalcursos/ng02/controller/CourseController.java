package com.portalcursos.ng02.controller;

import com.portalcursos.ng02.model.Course;
import com.portalcursos.ng02.service.CourseService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.context.SecurityContextHolder;
import com.portalcursos.ng02.model.StaffMember;
import com.portalcursos.ng02.repository.StaffMemberRepository;
import com.portalcursos.ng02.service.UserDetailsImpl;
import com.portalcursos.ng02.dto.MessageResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import lombok.RequiredArgsConstructor;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/courses")
@RequiredArgsConstructor
public class CourseController {

    private static final Logger logger = LoggerFactory.getLogger(CourseController.class);

    private final CourseService courseService;
    private final StaffMemberRepository staffMemberRepository;

    @GetMapping
    public ResponseEntity<?> getAllCourses() {
        return ResponseEntity.ok(courseService.getAllCourses());
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getCourseById(@PathVariable UUID id) {
        return ResponseEntity.ok(courseService.getCourseById(id));
    }

    @PostMapping
    public ResponseEntity<?> createCourse(@RequestBody Course course) {
        injectAuditStamps(course);
        Course savedCourse = courseService.createCourse(course);
        logger.info("[ACADEMIC-API] [COURSE-CREATE] Curso criado com sucesso: {}", savedCourse.getDenominacaoCurso());
        return ResponseEntity.ok(savedCourse);
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> updateCourse(@PathVariable UUID id, @RequestBody Course courseDetails) {
        injectAuditStamps(courseDetails);
        Course updatedCourse = courseService.updateCourse(id, courseDetails);
        logger.info("[ACADEMIC-API] [COURSE-UPDATE] Curso ID {} atualizado.", id);
        return ResponseEntity.ok(updatedCourse);
    }

    private void injectAuditStamps(Course course) {
        if (SecurityContextHolder.getContext().getAuthentication() != null) {
            Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
            if (principal instanceof UserDetailsImpl) {
                UserDetailsImpl userDetails = (UserDetailsImpl) principal;
                staffMemberRepository.findById(userDetails.getId()).ifPresent(staff -> {
                    course.setCreator(staff);
                    logger.debug("[AUDIT] Auditor normalizado injetado: {} para o curso: {}", staff.getFullName(), course.getDenominacaoCurso());
                });
            }
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteCourse(@PathVariable UUID id) {
        courseService.deleteCourse(id);
        logger.warn("[ACADEMIC-API] [COURSE-DELETE] Curso ID {} removido.", id);
        return ResponseEntity.ok(new MessageResponse("Curso removido com sucesso."));
    }
}

