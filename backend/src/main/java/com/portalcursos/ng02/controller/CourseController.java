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
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@CrossOrigin(origins = "*", maxAge = 3600)
@RestController
@RequestMapping("/api/v1/courses")
public class CourseController {

    @Autowired
    private CourseService courseService;

    @Autowired
    private StaffMemberRepository staffMemberRepository;

    @GetMapping
    public ResponseEntity<?> getAllCourses() {
        try {
            return ResponseEntity.ok(courseService.getAllCourses());
        } catch (Exception e) {
            System.err.println("[CRITICAL] Erro ao listar cursos: " + e.getMessage());
            return ResponseEntity.internalServerError()
                    .body(new MessageResponse("Erro ao carregar lista de cursos."));
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getCourseById(@PathVariable UUID id) {
        try {
            return ResponseEntity.ok(courseService.getCourseById(id));
        } catch (Exception e) {
            System.err.println("[CRITICAL] Erro ao buscar curso ID " + id + ": " + e.getMessage());
            return ResponseEntity.status(404)
                    .body(new MessageResponse("Curso não encontrado ou erro na recuperação."));
        }
    }

    @PostMapping
    public ResponseEntity<?> createCourse(@RequestBody Course course) {
        try {
            injectAuditStamps(course);
            Course savedCourse = courseService.createCourse(course);
            return ResponseEntity.ok(savedCourse);
        } catch (Exception e) {
            System.err.println("[CRITICAL] Erro ao criar curso: " + e.getMessage());
            return ResponseEntity.badRequest().body(new MessageResponse("Falha ao criar curso: " + e.getMessage()));
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> updateCourse(@PathVariable UUID id, @RequestBody Course courseDetails) {
        try {
            injectAuditStamps(courseDetails);
            Course updatedCourse = courseService.updateCourse(id, courseDetails);
            return ResponseEntity.ok(updatedCourse);
        } catch (Exception e) {
            System.err.println("[CRITICAL] Erro ao atualizar curso ID " + id + ": " + e.getMessage());
            return ResponseEntity.badRequest().body(new MessageResponse("Erro na atualização: " + e.getMessage()));
        }
    }

    private void injectAuditStamps(Course course) {
        if (SecurityContextHolder.getContext().getAuthentication() != null) {
            Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
            if (principal instanceof UserDetailsImpl) {
                UserDetailsImpl userDetails = (UserDetailsImpl) principal;
                staffMemberRepository.findById(userDetails.getId()).ifPresent(staff -> {
                    course.setCreatorName(staff.getFullName());
                    course.setCreatorPosition(staff.getPosition());
                    course.setCreatorPhotoUrl(staff.getFotoUrl());
                });
            }
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteCourse(@PathVariable UUID id) {
        try {
            courseService.deleteCourse(id);
            return ResponseEntity.ok(new MessageResponse("Curso removido com sucesso."));
        } catch (Exception e) {
            System.err.println("[CRITICAL] Erro ao remover curso ID " + id + ": " + e.getMessage());
            return ResponseEntity.badRequest().body(new MessageResponse("Falha ao remover curso: " + e.getMessage()));
        }
    }
}
