package com.portalcursos.ng02.controller;

import com.portalcursos.ng02.model.PostgradStudent;
import com.portalcursos.ng02.model.StaffMember;
import com.portalcursos.ng02.repository.PostgradStudentRepository;
import com.portalcursos.ng02.repository.StaffMemberRepository;
import com.portalcursos.ng02.service.StorageService;
import com.portalcursos.ng02.service.UserDetailsImpl;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.transaction.annotation.Transactional;
import lombok.extern.slf4j.Slf4j;

import com.portalcursos.ng02.dto.MessageResponse;
import java.io.IOException;
import java.util.List;
import java.util.Optional;

@CrossOrigin(origins = "*", maxAge = 3600)
@RestController
@RequestMapping("/api/v1/postgrad-students")
@Slf4j
public class PostgradStudentController {

    @Autowired
    private PostgradStudentRepository studentRepository;

    @Autowired
    private StaffMemberRepository staffMemberRepository;

    @Autowired
    private StorageService storageService;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> listAll() {
        try {
            return ResponseEntity.ok(studentRepository.findAll());
        } catch (Exception e) {
            System.err.println("[CRITICAL] Erro ao listar alunos de pós-graduação: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.internalServerError()
                    .body(new MessageResponse("Erro interno ao recuperar lista de alunos de pós-graduação."));
        }
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> findById(@PathVariable Long id) {
        try {
            return studentRepository.findById(id)
                    .map(ResponseEntity::ok)
                    .orElse(ResponseEntity.notFound().build());
        } catch (Exception e) {
            System.err.println("[CRITICAL] Erro ao buscar aluno de pós-graduação ID " + id + ": " + e.getMessage());
            return ResponseEntity.internalServerError()
                    .body(new MessageResponse("Erro ao recuperar dados do aluno."));
        }
    }

    @PostMapping(consumes = "multipart/form-data")
    @Transactional
    public ResponseEntity<?> create(
            @RequestParam("fullName") String fullName,
            @RequestParam("email") String email,
            @RequestParam("cpf") String cpf,
            @RequestParam(value = "phone", required = false) String phone,
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
        if (studentRepository.existsByEmail(email)) {
            return ResponseEntity.badRequest().body(new MessageResponse("Erro: E-mail já cadastrado no sistema."));
        }
        if (studentRepository.existsByCpf(cpf)) {
            return ResponseEntity.badRequest().body(new MessageResponse("Erro: CPF já cadastrado no sistema."));
        }

        try {
            System.out.println("[SUPREME-POSTGRAD] Iniciando processo de matrícula para: " + fullName);
            
            PostgradStudent student = PostgradStudent.builder()
                    .registrationNumber("POS-" + java.util.UUID.randomUUID().toString().substring(0, 8).toUpperCase())
                    .fullName(fullName)
                    .email(email)
                    .cpf(cpf)
                    .phone(phone)
                    .dateOfBirth(dateOfBirth)
                    .address(address)
                    .graduationInstitution(graduationInstitution)
                    .graduationYear(graduationYear)
                    .desiredCourse(desiredCourse)
                    .enrollmentStatus("PENDENTE")
                    .build();

            // Forçar visibilidade (Soft Delete protection)
            student.setActive(true);

            // --- [AUDITORIA V31.4-ULTRA] ---
            injectAuditStamps(student);

            // PASSO 1: Persistência Atômica Base
            System.out.println("[SUPREME-POSTGRAD] Salvando registro base no banco de dados...");
            PostgradStudent savedInitial = studentRepository.saveAndFlush(student);
            System.out.println("[SUPREME-POSTGRAD] SUCESSO: Registro base persistido. ID: " + savedInitial.getId());

            // PASSO 2: Processamento Resiliente de Arquivos
            try {
                if (foto3x4File != null && !foto3x4File.isEmpty()) {
                    String path = storageService.store(foto3x4File, "postgrad/fotos-perfil");
                    savedInitial.setFotoMatricula(path);
                    System.out.println("[SUPREME-POSTGRAD] Foto processada: " + path);
                }
            } catch (Exception e) {
                System.err.println("[SUPREME-POSTGRAD] AVISO: Falha ao salvar Foto: " + e.getMessage());
            }

            try {
                if (diplomaFile != null && !diplomaFile.isEmpty()) {
                    savedInitial.setDiplomaFilePath(storageService.store(diplomaFile, "postgrad/diplomas"));
                }
                if (rgCpfFile != null && !rgCpfFile.isEmpty()) {
                    savedInitial.setRgCpfFilePath(storageService.store(rgCpfFile, "postgrad/documentos"));
                }
                if (proofOfAddressFile != null && !proofOfAddressFile.isEmpty()) {
                    savedInitial.setProofOfAddressFilePath(storageService.store(proofOfAddressFile, "postgrad/residencia"));
                }
                if (academicTranscriptFile != null && !academicTranscriptFile.isEmpty()) {
                    savedInitial.setAcademicTranscriptFilePath(storageService.store(academicTranscriptFile, "postgrad/historicos"));
                }
                System.out.println("[SUPREME-POSTGRAD] Documentos processados com sucesso.");
            } catch (Exception e) {
                System.err.println("[SUPREME-POSTGRAD] AVISO: Falha parcial no upload de documentos: " + e.getMessage());
            }

            // PASSO 3: Sincronização Final
            PostgradStudent finalSaved = studentRepository.saveAndFlush(savedInitial);
            System.out.println("[SUPREME-POSTGRAD] Matrícula concluída com sucesso total para ID: " + finalSaved.getId());
            
            return ResponseEntity.ok(finalSaved);

        } catch (Exception e) {
            System.err.println("[SUPREME-POSTGRAD] ERRO CRÍTICO NA MATRÍCULA: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.internalServerError()
                    .body(new MessageResponse("Erro crítico ao processar matrícula: " + e.getMessage()));
        }
    }

    @PutMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> update(
            @PathVariable Long id,
            @RequestParam("fullName") String fullName,
            @RequestParam(value = "phone", required = false) String phone,
            @RequestParam(value = "address", required = false) String address,
            @RequestParam("desiredCourse") String desiredCourse,
            @RequestParam("enrollmentStatus") String enrollmentStatus,
            @RequestParam(value = "foto3x4File", required = false) MultipartFile foto3x4File
    ) {
        try {
            Optional<PostgradStudent> studentOpt = studentRepository.findById(id);
            if (studentOpt.isEmpty()) {
                return ResponseEntity.status(404).body(new MessageResponse("Estudante de pós-graduação não encontrado."));
            }

            PostgradStudent student = studentOpt.get();
            student.setFullName(fullName);
            student.setPhone(phone);
            student.setAddress(address);
            student.setDesiredCourse(desiredCourse);
            student.setEnrollmentStatus(enrollmentStatus);

            if (foto3x4File != null && !foto3x4File.isEmpty()) {
                try {
                    storageService.delete(student.getFotoMatricula());
                    String fileName = storageService.store(foto3x4File, "fotos-perfil");
                    student.setFotoMatricula(fileName);
                } catch (IOException e) {
                    System.err.println("[SUPREME-ERROR] Erro ao processar foto: " + e.getMessage());
                }
            }

            injectAuditStamps(student);
            return ResponseEntity.ok(studentRepository.save(student));
        } catch (Exception e) {
            System.err.println("[SUPREME-ERROR] Erro ao atualizar pós-graduando: " + e.getMessage());
            return ResponseEntity.internalServerError().body(new MessageResponse("Erro ao atualizar dados do pós-graduando."));
        }
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> updateStatus(@PathVariable Long id, @RequestParam("status") String status) {
        try {
            return studentRepository.findById(id).map(student -> {
                student.setEnrollmentStatus(status);
                injectAuditStamps(student);
                return ResponseEntity.ok(studentRepository.saveAndFlush(student));
            }).orElse(ResponseEntity.notFound().build());
        } catch (Exception e) {
            System.err.println("[SUPREME-ERROR] Erro ao atualizar status Pós: " + e.getMessage());
            return ResponseEntity.internalServerError().body(new MessageResponse("Erro ao atualizar status da matrícula de pós-graduação."));
        }
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> delete(@PathVariable Long id) {
        try {
            return studentRepository.findById(id).map(student -> {
                studentRepository.delete(student);
                return ResponseEntity.ok(new MessageResponse("Aluno de pós-graduação desativado com sucesso (Soft Delete)."));
            }).orElse(ResponseEntity.notFound().build());
        } catch (Exception e) {
            System.err.println("[CRITICAL] Erro ao desativar aluno ID " + id + ": " + e.getMessage());
            return ResponseEntity.internalServerError()
                    .body(new MessageResponse("Falha ao processar exclusão lógica do aluno."));
        }
    }

    private void injectAuditStamps(PostgradStudent s) {
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
