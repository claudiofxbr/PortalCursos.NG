package com.portalcursos.ng02.controller;

import com.portalcursos.ng02.model.StaffMember;
import com.portalcursos.ng02.model.Student;
import com.portalcursos.ng02.model.Teacher;
import com.portalcursos.ng02.model.Role;
import com.portalcursos.ng02.repository.StaffMemberRepository;
import com.portalcursos.ng02.service.AuthorityHierarchyService;
import com.portalcursos.ng02.repository.StudentRepository;
import com.portalcursos.ng02.repository.TeacherRepository;
import com.portalcursos.ng02.dto.StudentListDTO;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.http.ResponseEntity;
import com.portalcursos.ng02.exception.BusinessException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
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
    private final AuthorityHierarchyService authorityHierarchyService;

    @GetMapping("/students")
    @PreAuthorize("hasAnyRole('ADMIN', 'SECRETARIA', 'ACADEMICO', 'ROOT_MASTER')")
    public ResponseEntity<?> getAllStudents(@RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size) {
        if (page != null || size != null) {
            int p = page == null ? 0 : page;
            int s = size == null ? 50 : size;
            if (p < 0 || s < 1 || s > 200) {
                throw new BusinessException("Paginação inválida: page >= 0 e size entre 1 e 200");
            }
            Page<Long> ids = studentRepository.findActiveStudentIds(PageRequest.of(p, s, Sort.by(Sort.Direction.DESC, "id")));
            Map<Long, Student> byId = new HashMap<>();
            if (!ids.isEmpty()) {
                studentRepository.findAllWithCourseAndCreatorByIdIn(ids.getContent()).forEach(st -> byId.put(st.getId(), st));
            }
            List<StudentListDTO> body = ids.getContent().stream().map(byId::get).filter(java.util.Objects::nonNull).map(StudentListDTO::from).toList();
            return ResponseEntity.ok().header("X-Total-Count", String.valueOf(ids.getTotalElements())).body(body);
        }
        return ResponseEntity.ok(studentRepository.findAllWithCourseAndCreator().stream()
                .map(StudentListDTO::from)
                .toList());
    }

    @GetMapping("/teachers")
    @PreAuthorize("hasAnyRole('ADMIN', 'SECRETARIA', 'ACADEMICO', 'ROOT_MASTER')")
    public ResponseEntity<?> getAllTeachers() {
        return ResponseEntity.ok(teacherRepository.findAll());
    }

    @GetMapping("/staff")
    @PreAuthorize("hasAnyRole('ADMIN', 'ROOT_MASTER')")
    public ResponseEntity<?> getAllStaff() {
        return ResponseEntity.ok(staffMemberRepository.findAllByActiveTrue());
    }

    @GetMapping("/users")
    @PreAuthorize("hasAnyRole('ADMIN', 'ROOT_MASTER')")
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

        Map<Long, Role.ERole> highestRoleByStaff = new HashMap<>();
        for (Object[] row : staffMemberRepository.findActiveStaffRoles()) {
            highestRoleByStaff.merge((Long) row[0], (Role.ERole) row[1],
                    (a, b) -> authorityHierarchyService.getRoleLevel(b) > authorityHierarchyService.getRoleLevel(a) ? b : a);
        }

        staffMemberRepository.findAllByActiveTrue().forEach(sm -> {
            Map<String, Object> u = new HashMap<>();
            u.put("id", "M_" + sm.getId());
            u.put("name", sm.getFullName());
            u.put("doc", sm.getPosition());
            u.put("course", sm.getDepartment());
            Role.ERole role = highestRoleByStaff.get(sm.getId());
            u.put("role", role != null ? role.name().replaceFirst("^ROLE_", "") : "COLABORADOR");
            allUsers.add(u);
        });

        return ResponseEntity.ok(allUsers);
    }
}

