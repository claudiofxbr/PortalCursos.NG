package com.portalcursos.ng02.service;

import com.portalcursos.ng02.model.*;
import com.portalcursos.ng02.repository.PostgradStudentRepository;
import com.portalcursos.ng02.repository.StaffMemberRepository;
import com.portalcursos.ng02.repository.StudentDocumentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class PostgradStudentService {

    private final PostgradStudentRepository studentRepository;
    private final StudentDocumentRepository documentRepository;
    private final StorageService storageService;

    public List<PostgradStudent> findAll() {
        return studentRepository.findAllActive();
    }

    public Optional<PostgradStudent> findById(Long id) {
        return studentRepository.findByIdAndActiveTrue(id);
    }

    @Transactional(rollbackFor = Exception.class)
    public PostgradStudent create(PostgradStudent student, 
                                MultipartFile diplomaFile, 
                                MultipartFile rgCpfFile,
                                MultipartFile proofOfAddressFile, 
                                MultipartFile academicTranscriptFile, 
                                MultipartFile foto3x4File) {
        
        log.info("[SERVICE-POSTGRAD] Iniciando processo de matrícula para: {}", student.getFullName());
        
        student.setCpf(sanitizeNumber(student.getCpf()));
        student.setPhone(sanitizeNumber(student.getPhone()));
        
        student.setRegistrationNumber("POS-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        student.setEnrollmentStatus("PENDENTE");
        student.setActive(true);
        
        // Salva registro base
        PostgradStudent saved = studentRepository.saveAndFlush(student);

        // Processamento de arquivos via StudentDocument
        try {
            if (foto3x4File != null && !foto3x4File.isEmpty()) {
                String path = storageService.store(foto3x4File, "postgrad/fotos-perfil");
                saved.setFotoMatricula(path);
            }
            if (diplomaFile != null && !diplomaFile.isEmpty()) {
                String path = storageService.store(diplomaFile, "postgrad/diplomas");
                addDocument(saved, path, EDocumentType.DIPLOMA);
            }
            if (rgCpfFile != null && !rgCpfFile.isEmpty()) {
                String path = storageService.store(rgCpfFile, "postgrad/documentos");
                addDocument(saved, path, EDocumentType.RG);
            }
            if (proofOfAddressFile != null && !proofOfAddressFile.isEmpty()) {
                String path = storageService.store(proofOfAddressFile, "postgrad/residencia");
                addDocument(saved, path, EDocumentType.COMPROVANTE_RESIDENCIA);
            }
            if (academicTranscriptFile != null && !academicTranscriptFile.isEmpty()) {
                String path = storageService.store(academicTranscriptFile, "postgrad/historicos");
                addDocument(saved, path, EDocumentType.HISTORICO_ESCOLAR);
            }
        } catch (IOException e) {
            log.error("[SERVICE-POSTGRAD] Falha no processamento de arquivos: {}", e.getMessage());
            throw new RuntimeException("Falha ao salvar documentos do estudante: " + e.getMessage());
        }

        return studentRepository.save(saved);
    }

    private void addDocument(Student student, String path, EDocumentType type) {
        StudentDocument doc = StudentDocument.builder()
                .documentType(type)
                .filePath(path)
                .status(EDocumentStatus.PENDING)
                .uploadDate(LocalDateTime.now())
                .student(student)
                .build();
        student.getDocuments().add(doc);
    }

    @Transactional
    public PostgradStudent update(Long id, PostgradStudent updatedData, MultipartFile foto3x4File) throws IOException {
        PostgradStudent student = studentRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Estudante não encontrado"));

        student.setFullName(updatedData.getFullName());
        student.setPhone(sanitizeNumber(updatedData.getPhone()));
        student.setAddress(updatedData.getAddress());
        student.setDesiredCourse(updatedData.getDesiredCourse());
        student.setEnrollmentStatus(updatedData.getEnrollmentStatus());

        if (foto3x4File != null && !foto3x4File.isEmpty()) {
            if (student.getFotoMatricula() != null) {
                storageService.delete(student.getFotoMatricula());
            }
            String fileName = storageService.store(foto3x4File, "fotos-perfil");
            student.setFotoMatricula(fileName);
        }

        return studentRepository.save(student);
    }

    @Transactional
    public void delete(Long id) {
        studentRepository.findById(id).ifPresent(student -> {
            student.setActive(false);
            studentRepository.save(student);
        });
    }

    private String sanitizeNumber(String value) {
        if (value == null) return null;
        return value.replaceAll("[^0-9]", "");
    }

    public boolean existsByEmail(String email) {
        return studentRepository.existsByEmailGlobal(email);
    }

    public boolean existsByCpf(String cpf) {
        return studentRepository.existsByCpfGlobal(cpf);
    }

}
