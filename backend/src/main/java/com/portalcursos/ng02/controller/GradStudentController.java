package com.portalcursos.ng02.controller;

import com.portalcursos.ng02.model.*;
import com.portalcursos.ng02.repository.StudentRepository;
import com.portalcursos.ng02.repository.StaffMemberRepository;
import com.portalcursos.ng02.service.StorageService;
import com.portalcursos.ng02.service.UserDetailsImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import com.portalcursos.ng02.dto.MessageResponse;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/grad-students")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class GradStudentController {

    private final StudentRepository studentRepository;
    private final StaffMemberRepository staffMemberRepository;
    private final StorageService storageService;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<Student>> getAllGradStudents() {
        return ResponseEntity.ok(studentRepository.findAll());
    }

    @PostMapping("/enroll")
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
            // Arquivos
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

        if (studentRepository.findByEmail(email).isPresent()) {
            return ResponseEntity.badRequest().body("Email já cadastrado.");
        }
        if (studentRepository.findByCpf(cpf).isPresent()) {
            return ResponseEntity.badRequest().body("CPF já cadastrado.");
        }

        try {
            Student.StudentBuilder<?, ?> studentBuilder = Student.builder()
                    .registrationNumber("GRAD-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase())
                    .fullName(fullName)
                    .email(email)
                    .cpf(cpf)
                    .phone(phone)
                    .dateOfBirth(dateOfBirth)
                    .address(address)
                    .currentCourse(currentCourse)
                    .enrollmentStatus("PENDENTE_VALIDACAO")
                    .nacionalidade(nacionalidade)
                    .estadoCivil(estadoCivil)
                    .sexo(sexo)
                    .numeroReservista(numeroReservista)
                    .tituloEleitor(tituloEleitor)
                    .isEstrangeiro(isEstrangeiro)
                    .formaIngresso(formaIngresso)
                    .tipoCota(tipoCota)
                    .documents(new ArrayList<>());

            Student student = studentBuilder.build();

            // --- [AUDITORIA V30.9-SUPREME] ---
            injectAuditStamps(student);

            // Persistir primeiro o estudante para gerar ID
            Student savedStudent = studentRepository.save(student);

            // Processar Documentos usando o StorageService unificado
            String fotoPath = addDocumentAndReturnPath(savedStudent, foto3x4, EDocumentType.FOTO_3X4);
            if (fotoPath != null) {
                savedStudent.setFotoUrl(fotoPath);
            }
            addDocument(savedStudent, rgCpf, EDocumentType.RG);
            addDocument(savedStudent, comprovanteResidencia, EDocumentType.COMPROVANTE_RESIDENCIA);
            addDocument(savedStudent, certificadoEM, EDocumentType.CERTIFICADO_EM);
            addDocument(savedStudent, historicoEM, EDocumentType.HISTORICO_EM);
            addDocument(savedStudent, enemSisu, EDocumentType.ENEM_SISU);
            addDocument(savedStudent, diplomaAnt, EDocumentType.DIPLOMA_ANT);
            addDocument(savedStudent, historicoIesAnt, EDocumentType.HISTORICO_IES_ANT);
            addDocument(savedStudent, laudoMedico, EDocumentType.LAUDO_MEDICO);
            addDocument(savedStudent, rnmRne, EDocumentType.RNM_RNE);
            addDocument(savedStudent, tituloEleitorFile, EDocumentType.TITULO_ELEITOR);
            addDocument(savedStudent, reservistaFile, EDocumentType.CERTIFICADO_RESERVISTA);
            addDocument(savedStudent, certidaoNascimentoFile, EDocumentType.CERTIDAO_NASCIMENTO);
            addDocument(savedStudent, autodeclaracaoRacialFile, EDocumentType.AUTODECLARACAO_RACIAL);

            return ResponseEntity.ok(studentRepository.save(savedStudent));

        } catch (IOException e) {
            return ResponseEntity.internalServerError().body("Erro ao processar matrícula: " + e.getMessage());
        }
    }

    private String addDocumentAndReturnPath(Student student, MultipartFile file, EDocumentType type) throws IOException {
        if (file != null && !file.isEmpty()) {
            String fileName = storageService.store(file, "grad-students/" + type.name().toLowerCase());
            StudentDocument doc = StudentDocument.builder()
                    .documentType(type)
                    .filePath(fileName)
                    .status(EDocumentStatus.PENDING)
                    .uploadDate(LocalDateTime.now())
                    .student(student)
                    .build();
            student.getDocuments().add(doc);
            return fileName;
        }
        return null;
    }

    private void addDocument(Student student, MultipartFile file, EDocumentType type) throws IOException {
        addDocumentAndReturnPath(student, file, type);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Student> getStudent(@PathVariable Long id) {
        if (id == null) return ResponseEntity.badRequest().build();
        return studentRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('STAFF')")
    public ResponseEntity<?> updateStudent(
            @PathVariable Long id,
            @RequestParam("fullName") String fullName,
            @RequestParam("phone") String phone,
            @RequestParam("address") String address,
            @RequestParam("currentCourse") String currentCourse,
            @RequestParam("enrollmentStatus") String enrollmentStatus,
            @RequestParam(value = "foto3x4", required = false) MultipartFile foto3x4
    ) {
        return studentRepository.findById(id).map(student -> {
            student.setFullName(fullName);
            student.setPhone(phone);
            student.setAddress(address);
            student.setCurrentCourse(currentCourse);
            student.setEnrollmentStatus(enrollmentStatus);

            if (foto3x4 != null && !foto3x4.isEmpty()) {
                try {
                    storageService.delete(student.getFotoUrl());
                    String fileName = storageService.store(foto3x4, "grad-students/perfil");
                    student.setFotoUrl(fileName);
                } catch (IOException e) {
                    // Log error
                }
            }

            // Atualizar auditoria SUPREME
            injectAuditStamps(student);

            return ResponseEntity.ok(studentRepository.save(student));
        }).orElse(ResponseEntity.notFound().build());
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<?> updateStatus(@PathVariable Long id, @RequestParam("status") String status) {
        if (id == null) return ResponseEntity.badRequest().build();
        return studentRepository.findById(id).map(student -> {
            student.setEnrollmentStatus(status);
            
            // Injetar auditoria SUPREME
            injectAuditStamps(student);
            
            return ResponseEntity.ok(studentRepository.save(student));
        }).orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteStudent(@PathVariable Long id) {
        if (id == null) return ResponseEntity.badRequest().build();
        return studentRepository.findById(id)
                .map(student -> {
                    studentRepository.delete(student);
                    return ResponseEntity.ok(new MessageResponse("Aluno de graduação desativado com sucesso (Soft Delete)."));
                })
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /**
     * Auxiliar para injetar os carimbos de auditoria Protocolo V30.9-SUPREME.
     */
    private void injectAuditStamps(Student s) {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (principal instanceof UserDetailsImpl) {
            UserDetailsImpl userDetails = (UserDetailsImpl) principal;
            staffMemberRepository.findByUserId(userDetails.getId()).ifPresent(staff -> {
                s.setCreatorName(staff.getFullName());
                s.setCreatorPosition(staff.getPosition());
                s.setCreatorPhotoUrl(staff.getFotoUrl());
            });
        }
    }
}
