package com.portalcursos.ng02.controller;

import com.portalcursos.ng02.model.StaffMember;
import com.portalcursos.ng02.model.Student;
import com.portalcursos.ng02.model.Teacher;
import com.portalcursos.ng02.repository.StaffMemberRepository;
import com.portalcursos.ng02.repository.StudentRepository;
import com.portalcursos.ng02.repository.TeacherRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@CrossOrigin(origins = "*", maxAge = 3600)
@RestController
@RequestMapping("/api/academic")
public class AcademicController {

    @Autowired
    StudentRepository studentRepository;

    @Autowired
    TeacherRepository teacherRepository;

    @Autowired
    StaffMemberRepository staffMemberRepository;

    @GetMapping("/students")
    @PreAuthorize("hasRole('ADMIN') or hasRole('STAFF')")
    public ResponseEntity<?> getAllStudents() {
        try {
            return ResponseEntity.ok(studentRepository.findAll());
        } catch (Exception e) {
            System.err.println("[SUPREME-ERROR] Erro ao listar estudantes: " + e.getMessage());
            return ResponseEntity.internalServerError().body(new com.portalcursos.ng02.payload.response.MessageResponse("Erro ao carregar estudantes."));
        }
    }

    @GetMapping("/teachers")
    @PreAuthorize("hasRole('ADMIN') or hasRole('STAFF')")
    public ResponseEntity<?> getAllTeachers() {
        try {
            return ResponseEntity.ok(teacherRepository.findAll());
        } catch (Exception e) {
            System.err.println("[SUPREME-ERROR] Erro ao listar professores: " + e.getMessage());
            return ResponseEntity.internalServerError().body(new com.portalcursos.ng02.payload.response.MessageResponse("Erro ao carregar professores."));
        }
    }

    @GetMapping("/staff")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> getAllStaff() {
        try {
            return ResponseEntity.ok(staffMemberRepository.findAll());
        } catch (Exception e) {
            System.err.println("[SUPREME-ERROR] Erro ao listar staff: " + e.getMessage());
            return ResponseEntity.internalServerError().body(new com.portalcursos.ng02.payload.response.MessageResponse("Erro ao carregar membros da equipe."));
        }
    }

    @GetMapping("/users")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> getAllAcademicUsers() {
        try {
            List<Map<String, Object>> allUsers = new ArrayList<>();

            studentRepository.findAll().forEach(s -> {
                Map<String, Object> u = new HashMap<>();
                u.put("id", "S_" + s.getId());
                u.put("name", s.getFullName());
                u.put("doc", s.getRegistrationNumber());
                u.put("course", s.getCurrentCourse());
                u.put("role", "ALUNO");
                allUsers.add(u);
            });

            teacherRepository.findAll().forEach(t -> {
                Map<String, Object> u = new HashMap<>();
                u.put("id", "T_" + t.getId());
                u.put("name", t.getFullName());
                u.put("doc", t.getDepartment());
                u.put("course", t.getSpecialization());
                u.put("role", "PROFESSOR");
                allUsers.add(u);
            });

            staffMemberRepository.findAll().forEach(sm -> {
                Map<String, Object> u = new HashMap<>();
                u.put("id", "M_" + sm.getId());
                u.put("name", sm.getFullName());
                u.put("doc", sm.getPosition());
                u.put("course", sm.getDepartment());
                u.put("role", "STAFF");
                allUsers.add(u);
            });

            return ResponseEntity.ok(allUsers);
        } catch (Exception e) {
            System.err.println("[SUPREME-ERROR] Erro na lista unificada acadêmica: " + e.getMessage());
            return ResponseEntity.internalServerError().body(new com.portalcursos.ng02.payload.response.MessageResponse("Erro ao processar lista acadêmica."));
        }
    }
}
