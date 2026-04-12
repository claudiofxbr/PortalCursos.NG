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
import com.portalcursos.ng02.repository.StudentDocumentRepository;
import com.portalcursos.ng02.repository.PaymentRepository;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import lombok.extern.slf4j.Slf4j;
import com.portalcursos.ng02.dto.MessageResponse;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/grad-students")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
@Slf4j
public class GradStudentController {

    private final StudentRepository studentRepository;
    private final StaffMemberRepository staffMemberRepository;
    private final StudentDocumentRepository documentRepository;
    private final PaymentRepository paymentRepository;
    private final StorageService storageService;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> getAllGradStudents() {
        try {
            List<Student> students = studentRepository.findAll();
            return ResponseEntity.ok(students);
        } catch (Exception e) {
            System.err.println("CRITICAL ERROR in getAllGradStudents: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new MessageResponse("Erro ao carregar lista de alunos: " + e.getMessage()));
        }
    }

    @PostMapping("/enroll")
    @Transactional
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

        try {
            if (studentRepository.findByEmail(email).isPresent()) {
                return ResponseEntity.badRequest().body(new MessageResponse("Email já cadastrado."));
            }
            if (studentRepository.findByCpf(cpf).isPresent()) {
                return ResponseEntity.badRequest().body(new MessageResponse("CPF já cadastrado."));
            }

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
            student.setActive(true); // Reforço absoluto de visibilidade
            injectAuditStamps(student);

            System.out.println("[LOG-SUPREME] 1/4: Iniciando persistência básica do aluno: " + student.getFullName());
            Student savedStudent;
            try {
                savedStudent = studentRepository.saveAndFlush(student);
                System.out.println("[LOG-SUPREME] 2/4: Registro básico salvo com sucesso no Neon. ID: " + savedStudent.getId());
            } catch (Exception e) {
                System.err.println("[LOG-SUPREME] ERRO CRÍTICO NA GRAVAÇÃO BÁSICA: " + e.getMessage());
                throw e; // Falha no cadastro base é fatal
            }

            // Processamento de Foto de Perfil (Prioritário)
            if (foto3x4 != null && !foto3x4.isEmpty()) {
                System.out.println("[LOG-SUPREME] 3/4: Processando upload de foto...");
                try {
                    String fotoPath = storageService.store(foto3x4, "fotos-perfil");
                    if (fotoPath != null) {
                        savedStudent.setFotoMatricula(fotoPath);
                        StudentDocument photoDoc = StudentDocument.builder()
                                .documentType(EDocumentType.RG) 
                                .filePath(fotoPath)
                                .status(EDocumentStatus.PENDING)
                                .student(savedStudent)
                                .build();
                        savedStudent.getDocuments().add(photoDoc);
                        studentRepository.saveAndFlush(savedStudent);
                        System.out.println("[LOG-SUPREME] -> Foto de perfil salva e vinculada.");
                    }
                } catch (Exception e) {
                    System.err.println("[LOG-SUPREME] ALERTA: Falha ao salvar foto, mas cadastro prosseguindo: " + e.getMessage());
                }
            }

            // Processamento de Documentos de Apoio (Resiliente)
            System.out.println("[LOG-SUPREME] 4/4: Processando lista de documentos secundários...");
            try {
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
                
                studentRepository.saveAndFlush(savedStudent);
                System.out.println("[LOG-SUPREME] -> Documentos secundários processados.");
            } catch (Exception e) {
                System.err.println("[LOG-SUPREME] ALERTA: Erro em documentos secundários, mas aluno já existe: " + e.getMessage());
            }

            System.out.println("[LOG-SUPREME] MATRÍCULA FINALIZADA PARA: " + savedStudent.getFullName());
            return ResponseEntity.ok(savedStudent);

        } catch (Exception e) {
            System.err.println("[LOG-SUPREME] FALHA TOTAL NA MATRÍCULA: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.internalServerError().body(new MessageResponse("Erro crítico ao processar matrícula: " + e.getMessage()));
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
        try {
            return studentRepository.findById(id).map(student -> {
                student.setFullName(fullName);
                student.setPhone(phone);
                student.setAddress(address);
                student.setCurrentCourse(currentCourse);
                student.setEnrollmentStatus(enrollmentStatus);

                if (foto3x4 != null && !foto3x4.isEmpty()) {
                    try {
                        storageService.delete(student.getFotoMatricula());
                        String fileName = storageService.store(foto3x4, "fotos-perfil");
                        student.setFotoMatricula(fileName);
                    } catch (IOException e) {
                        System.err.println("[SUPREME-ERROR] Erro ao processar foto: " + e.getMessage());
                    }
                }

                injectAuditStamps(student);
                return ResponseEntity.ok(studentRepository.save(student));
            }).orElse(ResponseEntity.notFound().build());
        } catch (Exception e) {
            System.err.println("[SUPREME-ERROR] Erro ao atualizar estudante: " + e.getMessage());
            return ResponseEntity.internalServerError().body(new MessageResponse("Erro ao atualizar dados do aluno."));
        }
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<?> updateStatus(@PathVariable Long id, @RequestParam("status") String status) {
        try {
            return studentRepository.findById(id).map(student -> {
                student.setEnrollmentStatus(status);
                injectAuditStamps(student);
                return ResponseEntity.ok(studentRepository.save(student));
            }).orElse(ResponseEntity.notFound().build());
        } catch (Exception e) {
            System.err.println("[SUPREME-ERROR] Erro ao atualizar status: " + e.getMessage());
            return ResponseEntity.internalServerError().body(new MessageResponse("Erro ao atualizar status da matrícula."));
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteStudent(@PathVariable Long id) {
        try {
            if (id == null) return ResponseEntity.badRequest().build();
            return studentRepository.findById(id)
                    .map(student -> {
                        injectAuditStamps(student);
                        studentRepository.delete(student);
                        return ResponseEntity.ok(new MessageResponse("Aluno de graduação desativado com sucesso (Soft Delete)."));
                    })
                    .orElseGet(() -> ResponseEntity.notFound().build());
        } catch (Exception e) {
            System.err.println("[SUPREME-ERROR] Erro ao excluir/desativar aluno: " + e.getMessage());
            return ResponseEntity.internalServerError().body(new MessageResponse("Erro ao processar exclusão do aluno."));
        }
    }

    // --- PROCEDIMENTOS CRUD EXPANDIDOS (V31.1-ULTRA) ---

    @PatchMapping("/documents/{docId}/status")
    @PreAuthorize("hasRole('ADMIN') or hasRole('STAFF')")
    public ResponseEntity<?> updateDocumentStatus(
            @PathVariable Long docId,
            @RequestParam("status") EDocumentStatus status,
            @RequestParam(value = "reason", required = false) String reason) {
        
        return documentRepository.findById(docId).map(doc -> {
            doc.setStatus(status);
            doc.setRejectionReason(reason);
            documentRepository.save(doc);
            
            // Se for a foto de perfil, atualizar também a foto do aluno
            if (doc.getFilePath().contains("fotos-perfil")) {
                doc.getStudent().setFotoMatricula(doc.getFilePath());
            }

            return ResponseEntity.ok(new MessageResponse("Status do documento atualizado: " + status));
        }).orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/{id}/generate-fee")
    @PreAuthorize("hasRole('ADMIN') or hasRole('STAFF')")
    public ResponseEntity<?> generateEnrollmentFee(@PathVariable Long id) {
        return studentRepository.findById(id).map(student -> {
            // Verificar se já existe taxa de matrícula ativa
            boolean exists = student.getPayments().stream()
                    .anyMatch(p -> p.getCategory() == EPaymentCategory.ENROLLMENT_FEE && p.isActive());
            
            if (exists) {
                return ResponseEntity.badRequest().body(new MessageResponse("Taxa de matrícula já gerada anteriormente."));
            }

            Payment fee = Payment.builder()
                    .student(student)
                    .amount(new java.math.BigDecimal("150.00")) // Valor padrão ou configure dinâmico
                    .dueDate(java.time.LocalDate.now().plusDays(5))
                    .status(EPaymentStatus.PENDING)
                    .category(EPaymentCategory.ENROLLMENT_FEE)
                    .academicLevel(EAcademicLevel.GRADUATION)
                    .description("Taxa de Matrícula - Processo Acadêmico Robust")
                    .active(true)
                    .build();
            
            // Injetar auditoria SUPREME na geração da taxa
            injectAuditStampsInPayment(fee);
            paymentRepository.save(fee);

            return ResponseEntity.ok(new MessageResponse("Taxa de matrícula gerada com sucesso para " + student.getFullName()));
        }).orElse(ResponseEntity.notFound().build());
    }

    private void injectAuditStampsInPayment(Payment p) {
        if (SecurityContextHolder.getContext().getAuthentication() != null) {
            Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
            if (principal instanceof UserDetailsImpl) {
                UserDetailsImpl userDetails = (UserDetailsImpl) principal;
                staffMemberRepository.findByUserId(userDetails.getId()).ifPresent(staff -> {
                    p.setCreatorName(staff.getFullName());
                    p.setCreatorPosition(staff.getPosition());
                    p.setCreatorPhotoUrl(staff.getFotoUrl());
                });
            }
        }
    }

    private void injectAuditStamps(Student s) {
        if (SecurityContextHolder.getContext().getAuthentication() != null) {
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
}
