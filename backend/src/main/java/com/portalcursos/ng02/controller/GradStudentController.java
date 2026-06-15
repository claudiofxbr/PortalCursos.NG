package com.portalcursos.ng02.controller;

import com.portalcursos.ng02.model.*;
import com.portalcursos.ng02.service.AuditService;
import com.portalcursos.ng02.service.StudentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import com.portalcursos.ng02.dto.MessageResponse;

import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/v1/grad-students")
@RequiredArgsConstructor
@Slf4j
public class GradStudentController {

    private final StudentService studentService;
    private final com.portalcursos.ng02.repository.CourseRepository courseRepository;
    private final AuditService auditService;

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
            @RequestParam(value = "autodeclaracaoRacialFile", required = false) MultipartFile autodeclaracaoRacialFile) throws java.io.IOException {

        if (studentService.existsByEmailGlobal(email)) {
            return ResponseEntity.badRequest().body(new MessageResponse("Email já cadastrado em nossa base de dados."));
        }
        if (studentService.existsByCpfGlobal(cpf)) {
            return ResponseEntity.badRequest().body(new MessageResponse("CPF já cadastrado em nossa base de dados."));
        }

        Student student = Student.builder()
                .fullName(fullName)
                .email(email)
                .cpf(cpf)
                .phone(phone)
                .dateOfBirth(dateOfBirth)
                .address(address)
                .course(courseRepository.findByDenominacaoCurso(currentCourse).orElse(null))
                .nacionalidade(nacionalidade)
                .estadoCivil(estadoCivil)
                .sexo(sexo)
                .numeroReservista(numeroReservista)
                .tituloEleitor(tituloEleitor)
                .isEstrangeiro(isEstrangeiro)
                .formaIngresso(formaIngresso)
                .tipoCota(tipoCota)
                .build();

        auditService.injectCreator(student);

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
    ) throws java.io.IOException {
        Student student = Student.builder()
                .fullName(fullName)
                .phone(phone)
                .address(address)
                .course(courseRepository.findByDenominacaoCurso(currentCourse).orElse(null))
                .enrollmentStatus(enrollmentStatus)
                .build();

        auditService.injectCreator(student);
        return ResponseEntity.ok(studentService.update(id, student, foto3x4));
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('ADMIN', 'SECRETARIA', 'ACADEMICO', 'ROOT_MASTER')")
    public ResponseEntity<?> updateStatus(@PathVariable Long id, @RequestParam("status") String status) throws java.io.IOException {
        Student student = studentService.findById(id)
                .orElseThrow(() -> new RuntimeException("Aluno não encontrado."));
        
        student.setEnrollmentStatus(status);
        auditService.injectCreator(student);
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
