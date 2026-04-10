package com.portalcursos.ng02.controller;

import com.portalcursos.ng02.model.PostgradStudent;
import com.portalcursos.ng02.dto.MessageResponse;
import com.portalcursos.ng02.repository.PostgradStudentRepository;
import com.portalcursos.ng02.service.StorageService;
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
            @RequestParam(value = "academicTranscriptFile", required = false) MultipartFile academicTranscriptFile
    ) {
        // Verificar duplicidades
        if (studentRepository.existsByEmail(email)) {
            return ResponseEntity.badRequest().body(new MessageResponse("Erro: E-mail já cadastrado no sistema."));
        }
        if (studentRepository.existsByCpf(cpf)) {
            return ResponseEntity.badRequest().body(new MessageResponse("Erro: CPF já cadastrado no sistema."));
        }

        try {
            // Fazer upload dos documentos
            String diplomaPath = storageService.store(diplomaFile, "diplomas");
            String rgPath = storageService.store(rgCpfFile, "documentos");
            String addressPath = storageService.store(proofOfAddressFile, "residencia");
            String transcriptPath = storageService.store(academicTranscriptFile, "historicos");

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
                    .build();

            PostgradStudent saved = studentRepository.save(student);
            return ResponseEntity.ok(saved);

        } catch (IOException e) {
            return ResponseEntity.internalServerError()
                    .body(new MessageResponse("Erro ao salvar documentos: " + e.getMessage()));
        }
    }

    // PUT /api/v1/postgrad-students/{id}/status - Atualizar status de matrícula
    @PutMapping("/{id}/status")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> updateStatus(@PathVariable Long id, @RequestParam String status) {
        return studentRepository.findById(id).map(student -> {
            student.setEnrollmentStatus(status);
            studentRepository.save(student);
            return ResponseEntity.ok(student);
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
            studentRepository.delete(student);
            return ResponseEntity.ok(new MessageResponse("Aluno removido com sucesso."));
        }).orElse(ResponseEntity.notFound().build());
    }
}
