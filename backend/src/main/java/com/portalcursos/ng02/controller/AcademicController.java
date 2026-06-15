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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import lombok.RequiredArgsConstructor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/academic")
@RequiredArgsConstructor
public class AcademicController {

    private final StudentRepository studentRepository;
    private final TeacherRepository teacherRepository;
    private final StaffMemberRepository staffMemberRepository;

    @GetMapping("/students")
    @PreAuthorize("hasAnyRole('ADMIN', 'SECRETARIA', 'ACADEMICO', 'ROOT_MASTER')")
    public ResponseEntity<?> getAllStudents() {
        return ResponseEntity.ok(studentRepository.findAllWithCourseAndCreator());
    }

    @GetMapping("/teachers")
    @PreAuthorize("hasAnyRole('ADMIN', 'SECRETARIA', 'ACADEMICO', 'ROOT_MASTER')")
    public ResponseEntity<?> getAllTeachers() {
        return ResponseEntity.ok(teacherRepository.findAll());
    }

    @GetMapping("/staff")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> getAllStaff() {
        return ResponseEntity.ok(staffMemberRepository.findAll());
    }

    @GetMapping("/users")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> getAllAcademicUsers() {
        List<Map<String, Object>> allUsers = new ArrayList<>();

        studentRepository.findAllWithCourse().forEach(s -> {
            Map<String, Object> u = new HashMap<>();
            u.put("id", "S_" + s.getId());
            u.put("name", s.getFullName());
            u.put("doc", s.getRegistrationNumber());
            u.put("course", s.getCourse() != null ? s.getCourse().getDenominacaoCurso() : "N/A");
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
    }
}

