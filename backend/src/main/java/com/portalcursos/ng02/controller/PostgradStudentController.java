package com.portalcursos.ng02.controller;
 
import com.portalcursos.ng02.model.PostgradStudent;
import com.portalcursos.ng02.service.PostgradStudentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import lombok.extern.slf4j.Slf4j;
import lombok.RequiredArgsConstructor;
 
import com.portalcursos.ng02.dto.MessageResponse;
import java.util.List;
import java.util.Optional;
 
@CrossOrigin(origins = "*", maxAge = 3600)
@RestController
@RequestMapping("/api/v1/postgrad-students")
@Slf4j
@RequiredArgsConstructor
public class PostgradStudentController {
 
    private final PostgradStudentService studentService;
    private final com.portalcursos.ng02.repository.StaffMemberRepository staffMemberRepository;

    private void injectAuditStamps(Object entity) {
        org.springframework.security.core.Authentication auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof com.portalcursos.ng02.service.UserDetailsImpl) {
            com.portalcursos.ng02.service.UserDetailsImpl userDetails = (com.portalcursos.ng02.service.UserDetailsImpl) auth.getPrincipal();
            staffMemberRepository.findById(userDetails.getId()).ifPresent(staff -> {
                if (entity instanceof PostgradStudent.PostgradStudentBuilder) {
                    ((PostgradStudent.PostgradStudentBuilder<?, ?>) entity).creator(staff);
                } else if (entity instanceof com.portalcursos.ng02.model.BaseAuditEntity) {
                    ((com.portalcursos.ng02.model.BaseAuditEntity) entity).setCreator(staff);
                }
            });
        }
    }
 
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'SECRETARIA', 'ACADEMICO', 'ROOT_MASTER')")
    public ResponseEntity<?> listAll() {
        log.info("[AUTH-GUARD] Usuário autorizado carregando lista de alunos de pós-graduação.");
        List<PostgradStudent> students = studentService.findAll();
        return ResponseEntity.ok(students);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SECRETARIA', 'ACADEMICO', 'ROOT_MASTER')")
    public ResponseEntity<?> findById(@PathVariable Long id) {
        return studentService.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping(consumes = "multipart/form-data")
    @PreAuthorize("hasAnyRole('ADMIN', 'SECRETARIA', 'MATRICULA', 'ROOT_MASTER')")
    public ResponseEntity<?> create(
            @RequestParam("fullName") String fullName,
            @RequestParam("email") String email,
            @RequestParam("cpf") String cpf,
            @RequestParam("phone") String phone,
            @RequestParam(value = "dateOfBirth", required = false) String dateOfBirth,
            @RequestParam(value = "address", required = false) String address,
            @RequestParam("graduationInstitution") String graduationInstitution,
            @RequestParam(value = "graduationYear", required = false) Integer graduationYear,
            @RequestParam("desiredCourse") String desiredCourse,
            @RequestParam(value = "diplomaFile", required = false) MultipartFile diplomaFile,
            @RequestParam(value = "rgCpfFile", required = false) MultipartFile rgCpfFile,
            @RequestParam(value = "proofOfAddressFile", required = false) MultipartFile proofOfAddressFile,
            @RequestParam(value = "academicTranscriptFile", required = false) MultipartFile academicTranscriptFile,
            @RequestParam(value = "foto3x4File", required = false) MultipartFile foto3x4File
    ) {
        if (studentService.existsByEmail(email)) {
            return ResponseEntity.badRequest().body(new MessageResponse("Erro: Este E-mail já possui um registro."));
        }
        if (studentService.existsByCpf(cpf)) {
            return ResponseEntity.badRequest().body(new MessageResponse("Erro: Este CPF já possui um registro."));
        }

        PostgradStudent.PostgradStudentBuilder<?, ?> studentBuilder = PostgradStudent.builder()
                .fullName(fullName)
                .email(email)
                .cpf(cpf)
                .phone(phone)
                .dateOfBirth(dateOfBirth)
                .address(address)
                .graduationInstitution(graduationInstitution)
                .graduationYear(graduationYear)
                .desiredCourse(desiredCourse);

        injectAuditStamps(studentBuilder);
        PostgradStudent student = studentBuilder.build();

        PostgradStudent finalSaved = studentService.create(student, diplomaFile, rgCpfFile, proofOfAddressFile, academicTranscriptFile, foto3x4File);
        return ResponseEntity.ok(finalSaved);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SECRETARIA', 'ROOT_MASTER')")
    public ResponseEntity<?> update(
            @PathVariable Long id,
            @RequestParam("fullName") String fullName,
            @RequestParam(value = "phone", required = false) String phone,
            @RequestParam(value = "address", required = false) String address,
            @RequestParam("desiredCourse") String desiredCourse,
            @RequestParam("enrollmentStatus") String enrollmentStatus,
            @RequestParam(value = "foto3x4File", required = false) MultipartFile foto3x4File
    ) {
        PostgradStudent student = PostgradStudent.builder()
                .fullName(fullName)
                .phone(phone)
                .address(address)
                .desiredCourse(desiredCourse)
                .enrollmentStatus(enrollmentStatus)
                .build();

        injectAuditStamps(student);

        return ResponseEntity.ok(studentService.update(id, student, foto3x4File));
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('ADMIN', 'SECRETARIA', 'ACADEMICO', 'ROOT_MASTER')")
    public ResponseEntity<?> updateStatus(@PathVariable Long id, @RequestParam("status") String status) {
        return studentService.findById(id).map(student -> {
            try {
                student.setEnrollmentStatus(status);
                injectAuditStamps(student);
                return ResponseEntity.ok(studentService.update(id, student, null));
            } catch (java.io.IOException e) {
                throw new RuntimeException("Erro ao atualizar status: falha de I/O", e);
            }
        }).orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'ROOT_MASTER')")
    public ResponseEntity<?> delete(@PathVariable Long id) {
        if (studentService.findById(id).isPresent()) {
            studentService.delete(id);
            return ResponseEntity.ok(new MessageResponse("Aluno desativado com sucesso."));
        }
        return ResponseEntity.notFound().build();
    }
}
