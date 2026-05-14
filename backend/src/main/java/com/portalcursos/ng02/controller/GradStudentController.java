package com.portalcursos.ng02.controller;

import com.portalcursos.ng02.model.*;
import com.portalcursos.ng02.service.StudentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import com.portalcursos.ng02.dto.MessageResponse;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/v1/grad-students")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
@Slf4j
public class GradStudentController {

    private final StudentService studentService;
    private final com.portalcursos.ng02.repository.StaffMemberRepository staffMemberRepository;

    private void injectAuditStamps(Object entity) {
        org.springframework.security.core.Authentication auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof com.portalcursos.ng02.service.UserDetailsImpl) {
            com.portalcursos.ng02.service.UserDetailsImpl userDetails = (com.portalcursos.ng02.service.UserDetailsImpl) auth.getPrincipal();
            staffMemberRepository.findById(userDetails.getId()).ifPresent(staff -> {
                if (entity instanceof Student.StudentBuilder) {
                    ((Student.StudentBuilder<?, ?>) entity).creator(staff);
                } else if (entity instanceof BaseAuditEntity) {
                    ((BaseAuditEntity) entity).setCreator(staff);
                }
            });
        }
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'SECRETARIA', 'ACADEMICO', 'ROOT_MASTER')")
    public ResponseEntity<?> getAllGradStudents() {
        log.info("[AUTH-GUARD] Listando alunos de graduação.");
        List<Student> students = studentService.findAll();
        return ResponseEntity.ok(students);
    }

    @PostMapping("/enroll")
    @PreAuthorize("hasAnyRole('ADMIN', 'SECRETARIA', 'MATRICULA', 'ROOT_MASTER')")
    public ResponseEntity<?> enrollStudent(
            @RequestParam("fullName") String fullName,
            @RequestParam("email") String email,
            @RequestParam("cpf") String cpf,
            @RequestParam("phone") String phone,
            @RequestParam("dateOfBirth") String dateOfBirth,
            @RequestParam("address") String address,
            @RequestParam("currentCourse") String currentCourse,
            @RequestParam("nacionalidade") String nacionalidade,
            @RequestParam("estadoCivil") String estadoCivil,
            @RequestParam("sexo") String sexo,
            @RequestParam(value = "numeroReservista", required = false) String numeroReservista,
            @RequestParam(value = "tituloEleitor", required = false) String tituloEleitor,
            @RequestParam("isEstrangeiro") boolean isEstrangeiro,
            @RequestParam("formaIngresso") EIngressMethod formaIngresso,
            @RequestParam("tipoCota") EQuotaType tipoCota,
            @RequestParam(value = "foto3x4", required = false) MultipartFile foto3x4,
            @RequestParam(value = "rgCpf", required = false) MultipartFile rgCpf,
            @RequestParam(value = "comprovanteResidencia", required = false) MultipartFile comprovanteResidencia,
            @RequestParam(value = "certificadoEM", required = false) MultipartFile certificadoEM,
            @RequestParam(value = "historicoEM", required = false) MultipartFile historicoEM,
            @RequestParam(value = "enemSisu", required = false) MultipartFile enemSisu,
            @RequestParam(value = "diplomaAnt", required = false) MultipartFile diplomaAnt,
            @RequestParam(value = "historicoIesAnt", required = false) MultipartFile historicoIesAnt,
            @RequestParam(value = "laudoMedico", required = false) MultipartFile laudoMedico,
            @RequestParam(value = "rnmRne", required = false) MultipartFile rnmRne,
            @RequestParam(value = "tituloEleitorFile", required = false) MultipartFile tituloEleitorFile,
            @RequestParam(value = "reservistaFile", required = false) MultipartFile reservistaFile,
            @RequestParam(value = "certidaoNascimentoFile", required = false) MultipartFile certidaoNascimentoFile,
            @RequestParam(value = "autodeclaracaoRacialFile", required = false) MultipartFile autodeclaracaoRacialFile) {

        if (studentService.findByEmail(email).isPresent()) {
            return ResponseEntity.badRequest().body(new MessageResponse("Email já cadastrado."));
        }
        if (studentService.findByCpf(cpf).isPresent()) {
            return ResponseEntity.badRequest().body(new MessageResponse("CPF já cadastrado."));
        }

        Student.StudentBuilder<?, ?> studentBuilder = Student.builder()
                .fullName(fullName)
                .email(email)
                .cpf(cpf)
                .phone(phone)
                .dateOfBirth(dateOfBirth)
                .address(address)
                .currentCourse(currentCourse)
                .nacionalidade(nacionalidade)
                .estadoCivil(estadoCivil)
                .sexo(sexo)
                .numeroReservista(numeroReservista)
                .tituloEleitor(tituloEleitor)
                .isEstrangeiro(isEstrangeiro)
                .formaIngresso(formaIngresso)
                .tipoCota(tipoCota);

        injectAuditStamps(studentBuilder);
        Student student = studentBuilder.build();

        List<StudentService.DocEntry> otherDocs = new ArrayList<>();
        otherDocs.add(new StudentService.DocEntry(rgCpf, EDocumentType.RG));
        otherDocs.add(new StudentService.DocEntry(comprovanteResidencia, EDocumentType.COMPROVANTE_RESIDENCIA));
        otherDocs.add(new StudentService.DocEntry(certificadoEM, EDocumentType.CERTIFICADO_EM));
        otherDocs.add(new StudentService.DocEntry(historicoEM, EDocumentType.HISTORICO_EM));
        otherDocs.add(new StudentService.DocEntry(enemSisu, EDocumentType.ENEM_SISU));
        otherDocs.add(new StudentService.DocEntry(diplomaAnt, EDocumentType.DIPLOMA_ANT));
        otherDocs.add(new StudentService.DocEntry(historicoIesAnt, EDocumentType.HISTORICO_IES_ANT));
        otherDocs.add(new StudentService.DocEntry(laudoMedico, EDocumentType.LAUDO_MEDICO));
        otherDocs.add(new StudentService.DocEntry(rnmRne, EDocumentType.RNM_RNE));
        otherDocs.add(new StudentService.DocEntry(tituloEleitorFile, EDocumentType.TITULO_ELEITOR));
        otherDocs.add(new StudentService.DocEntry(reservistaFile, EDocumentType.CERTIFICADO_RESERVISTA));
        otherDocs.add(new StudentService.DocEntry(certidaoNascimentoFile, EDocumentType.CERTIDAO_NASCIMENTO));
        otherDocs.add(new StudentService.DocEntry(autodeclaracaoRacialFile, EDocumentType.AUTODECLARACAO_RACIAL));

        Student enrolled = studentService.enroll(student, foto3x4, otherDocs);
        return ResponseEntity.ok(enrolled);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SECRETARIA', 'ACADEMICO', 'ROOT_MASTER')")
    public ResponseEntity<Student> getStudent(@PathVariable Long id) {
        return studentService.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SECRETARIA', 'ROOT_MASTER')")
    public ResponseEntity<?> updateStudent(
            @PathVariable Long id,
            @RequestParam("fullName") String fullName,
            @RequestParam("phone") String phone,
            @RequestParam("address") String address,
            @RequestParam("currentCourse") String currentCourse,
            @RequestParam("enrollmentStatus") String enrollmentStatus,
            @RequestParam(value = "foto3x4", required = false) MultipartFile foto3x4
    ) {
        Student student = Student.builder()
                .fullName(fullName)
                .phone(phone)
                .address(address)
                .currentCourse(currentCourse)
                .enrollmentStatus(enrollmentStatus)
                .build();

        injectAuditStamps(student);

        return ResponseEntity.ok(studentService.update(id, student, foto3x4));
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('ADMIN', 'SECRETARIA', 'ACADEMICO', 'ROOT_MASTER')")
    public ResponseEntity<?> updateStatus(@PathVariable Long id, @RequestParam("status") String status) {
        Student student = studentService.findById(id)
                .orElseThrow(() -> new RuntimeException("Aluno não encontrado."));
        
        student.setEnrollmentStatus(status);
        injectAuditStamps(student);
        return ResponseEntity.ok(studentService.update(id, student, null));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'ROOT_MASTER')")
    public ResponseEntity<?> deleteStudent(@PathVariable Long id) {
        Student student = studentService.findById(id)
                .orElseThrow(() -> new RuntimeException("Aluno não encontrado."));
        
        studentService.delete(id);
        return ResponseEntity.ok(new MessageResponse("Aluno desativado com sucesso."));
    }
}
