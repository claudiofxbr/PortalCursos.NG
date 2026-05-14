package com.portalcursos.ng02.service;

import com.portalcursos.ng02.model.PostgradStudent;
import com.portalcursos.ng02.repository.PostgradStudentRepository;
import com.portalcursos.ng02.repository.StaffMemberRepository;
import com.portalcursos.ng02.service.UserDetailsImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class PostgradStudentService {

    private final PostgradStudentRepository studentRepository;
    private final StorageService storageService;

    public List<PostgradStudent> findAll() {
        return studentRepository.findAll();
    }

    public Optional<PostgradStudent> findById(Long id) {
        return studentRepository.findById(id);
    }

    @Transactional(rollbackFor = Exception.class)
    public PostgradStudent create(PostgradStudent student, 
                                MultipartFile diplomaFile, 
                                MultipartFile rgCpfFile,
                                MultipartFile proofOfAddressFile, 
                                MultipartFile academicTranscriptFile, 
                                MultipartFile foto3x4File) {
        
        log.info("[SERVICE-POSTGRAD] Iniciando processo de matrícula para: {}", student.getFullName());
        
        student.setRegistrationNumber("POS-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        student.setEnrollmentStatus("PENDENTE");
        student.setActive(true);
        
        // Salva registro base
        PostgradStudent saved = studentRepository.saveAndFlush(student);

        // Processamento de arquivos
        try {
            if (foto3x4File != null && !foto3x4File.isEmpty()) {
                String path = storageService.store(foto3x4File, "postgrad/fotos-perfil");
                saved.setFotoMatricula(path);
            }
            if (diplomaFile != null && !diplomaFile.isEmpty()) {
                saved.setDiplomaFilePath(storageService.store(diplomaFile, "postgrad/diplomas"));
            }
            if (rgCpfFile != null && !rgCpfFile.isEmpty()) {
                saved.setRgCpfFilePath(storageService.store(rgCpfFile, "postgrad/documentos"));
            }
            if (proofOfAddressFile != null && !proofOfAddressFile.isEmpty()) {
                saved.setProofOfAddressFilePath(storageService.store(proofOfAddressFile, "postgrad/residencia"));
            }
            if (academicTranscriptFile != null && !academicTranscriptFile.isEmpty()) {
                saved.setAcademicTranscriptFilePath(storageService.store(academicTranscriptFile, "postgrad/historicos"));
            }
        } catch (IOException e) {
            log.error("[SERVICE-POSTGRAD] Falha no processamento de arquivos: {}", e.getMessage());
            throw new RuntimeException("Falha ao salvar documentos do estudante: " + e.getMessage());
        }

        return studentRepository.save(saved);
    }

    @Transactional
    public PostgradStudent update(Long id, PostgradStudent updatedData, MultipartFile foto3x4File) throws IOException {
        PostgradStudent student = studentRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Estudante não encontrado"));

        student.setFullName(updatedData.getFullName());
        student.setPhone(updatedData.getPhone());
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

    public void delete(Long id) {
        studentRepository.findById(id).ifPresent(student -> studentRepository.delete(student));
    }

    public boolean existsByEmail(String email) {
        return studentRepository.existsByEmailGlobal(email);
    }

    public boolean existsByCpf(String cpf) {
        return studentRepository.existsByCpfGlobal(cpf);
    }

}
