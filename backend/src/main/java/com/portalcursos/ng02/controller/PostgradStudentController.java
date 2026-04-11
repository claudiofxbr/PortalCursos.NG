package com.portalcursos.ng02.controller;

import com.portalcursos.ng02.model.StaffMember;
import com.portalcursos.ng02.repository.StaffMemberRepository;
import com.portalcursos.ng02.security.services.UserDetailsImpl;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

@CrossOrigin(origins = "*", maxAge = 3600)
@RestController
@RequestMapping("/api/v1/postgrad-students")
public class PostgradStudentController {

    @Autowired
    private PostgradStudentRepository studentRepository;

    @Autowired
    private StaffMemberRepository staffMemberRepository;

    @Autowired
    private StorageService storageService;

    // GET /api/v1/postgrad-students - Listar todos os alunos
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<PostgradStudent>> listAll() {
        return ResponseEntity.ok(studentRepository.findAll());
    }

    // GET /api/v1/postgrad-students/{id} - Buscar aluno por ID
    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> findById(@PathVariable Long id) {
        Optional<PostgradStudent> student = studentRepository.findById(id);
        if (student.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(student.get());
    }

    // POST /api/v1/postgrad-students - Cadastrar novo aluno com documentos
    @PostMapping(consumes = "multipart/form-data")
    @PreAuthorize("isAuthenticated()")
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
        // Verificar duplicidades
        if (studentRepository.existsByEmail(email)) {
            return ResponseEntity.badRequest().body(new MessageResponse("Erro: E-mail já cadastrado no sistema."));
        }
        if (studentRepository.existsByCpf(cpf)) {
            return ResponseEntity.badRequest().body(new MessageResponse("Erro: CPF já cadastrado no sistema."));
        }

        try {
            // --- [AUDITORIA V30.9-SUPREME] ---
            Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
            String creatorName = "Sistema";
            String creatorPosition = "Automático";
            String creatorPhotoUrl = null;

            if (principal instanceof UserDetailsImpl) {
                UserDetailsImpl userDetails = (UserDetailsImpl) principal;
                Optional<StaffMember> creatorStaff = staffMemberRepository.findByUserId(userDetails.getId());
                if (creatorStaff.isPresent()) {
                    creatorName = creatorStaff.get().getFullName();
                    creatorPosition = creatorStaff.get().getPosition();
                    creatorPhotoUrl = creatorStaff.get().getFotoUrl();
                }
            }

            // Fazer upload dos documentos
            String diplomaPath = storageService.store(diplomaFile, "diplomas");
            String rgPath = storageService.store(rgCpfFile, "documentos");
            String addressPath = storageService.store(proofOfAddressFile, "residencia");
            String transcriptPath = storageService.store(academicTranscriptFile, "historicos");
            String fotoPath = storageService.store(foto3x4File, "fotos-perfil");

            // Criar e salvar o aluno
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
                    .diplomaFilePath(diplomaPath)
                    .rgCpfFilePath(rgPath)
                    .proofOfAddressFilePath(addressPath)
                    .academicTranscriptFilePath(transcriptPath)
                    .fotoUrl(fotoPath)
                    .creatorName(creatorName)
                    .creatorPosition(creatorPosition)
                    .creatorPhotoUrl(creatorPhotoUrl)
                    .build();

            PostgradStudent saved = studentRepository.save(student);
            return ResponseEntity.ok(saved);

        } catch (IOException e) {
            return ResponseEntity.internalServerError()
                    .body(new MessageResponse("Erro ao salvar documentos: " + e.getMessage()));
        }
    }

    // PUT /api/v1/postgrad-students/{id}/status - Atualizar status de matrícula
                .orElse(ResponseEntity.notFound().build());
    }

    // PUT /api/v1/postgrad-students/{id} - Atualização completa do aluno
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
        return studentRepository.findById(id).map(student -> {
            student.setFullName(fullName);
            student.setPhone(phone);
            student.setAddress(address);
            student.setDesiredCourse(desiredCourse);
            student.setEnrollmentStatus(enrollmentStatus);

            if (foto3x4File != null && !foto3x4File.isEmpty()) {
                try {
                    storageService.delete(student.getFotoUrl());
                    String fotoPath = storageService.store(foto3x4File, "fotos-perfil");
                    student.setFotoUrl(fotoPath);
                } catch (IOException e) {
                    // Log error
                }
            }

            // Atualizar auditoria
            Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
            if (principal instanceof UserDetailsImpl) {
                UserDetailsImpl userDetails = (UserDetailsImpl) principal;
                staffMemberRepository.findByUserId(userDetails.getId()).ifPresent(creator -> {
                    student.setCreatorName(creator.getFullName());
                    student.setCreatorPosition(creator.getPosition());
                    student.setCreatorPhotoUrl(creator.getFotoUrl());
                });
            }

            return ResponseEntity.ok(studentRepository.save(student));
        }).orElse(ResponseEntity.notFound().build());
    }

    // DELETE /api/v1/postgrad-students/{id} - Remover aluno
    @DeleteMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> delete(@PathVariable Long id) {
        return studentRepository.findById(id).map(student -> {
            // Limpar arquivos do disco
            storageService.delete(student.getDiplomaFilePath());
            storageService.delete(student.getRgCpfFilePath());
            storageService.delete(student.getProofOfAddressFilePath());
            storageService.delete(student.getAcademicTranscriptFilePath());
            storageService.delete(student.getFotoUrl());
            studentRepository.delete(student);
            return ResponseEntity.ok(new MessageResponse("Aluno removido com sucesso."));
        }).orElse(ResponseEntity.notFound().build());
    }
}
