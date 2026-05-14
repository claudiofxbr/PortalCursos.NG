package com.portalcursos.ng02.service;

import com.portalcursos.ng02.model.*;
import com.portalcursos.ng02.repository.StudentRepository;
import com.portalcursos.ng02.repository.StaffMemberRepository;
import com.portalcursos.ng02.repository.StudentDocumentRepository;
import com.portalcursos.ng02.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;
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
public class StudentService {

    private final StudentRepository studentRepository;
    private final StudentDocumentRepository documentRepository;
    private final PaymentRepository paymentRepository;
    private final StorageService storageService;

    public List<Student> findAll() {
        return studentRepository.findAll();
    }

    public Optional<Student> findById(Long id) {
        return studentRepository.findById(id);
    }

    public Optional<Student> findByEmail(String email) {
        return studentRepository.findByEmail(email);
    }

    public Optional<Student> findByCpf(String cpf) {
        return studentRepository.findByCpf(cpf);
    }

    @Transactional(rollbackFor = Exception.class)
    public Student enroll(Student student, MultipartFile foto3x4, List<DocEntry> otherDocs) throws IOException {
        log.info("[SERVICE-GRAD] Iniciando matrícula para: {}", student.getFullName());
        
        student.setRegistrationNumber("GRAD-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        student.setEnrollmentStatus("PENDENTE_VALIDACAO");
        student.setActive(true);
        
        Student saved = studentRepository.save(student);

        if (foto3x4 != null && !foto3x4.isEmpty()) {
            String path = storageService.store(foto3x4, "fotos-perfil");
            saved.setFotoMatricula(path);
            addDocument(saved, path, EDocumentType.RG); 
        }

        for (DocEntry entry : otherDocs) {
            if (entry.file() != null && !entry.file().isEmpty()) {
                String path = storageService.store(entry.file(), "grad-students/" + entry.type().name().toLowerCase());
                addDocument(saved, path, entry.type());
            }
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
    public Student update(Long id, Student updatedData, MultipartFile foto3x4) throws IOException {
        Student student = studentRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Estudante não encontrado com ID: " + id));

        student.setFullName(updatedData.getFullName());
        student.setPhone(updatedData.getPhone());
        student.setAddress(updatedData.getAddress());
        student.setCurrentCourse(updatedData.getCurrentCourse());
        student.setEnrollmentStatus(updatedData.getEnrollmentStatus());

        if (foto3x4 != null && !foto3x4.isEmpty()) {
            if (student.getFotoMatricula() != null) {
                storageService.delete(student.getFotoMatricula());
            }
            String fileName = storageService.store(foto3x4, "fotos-perfil");
            student.setFotoMatricula(fileName);
        }

        return studentRepository.save(student);
    }

    @Transactional
    public void delete(Long id) {
        studentRepository.findById(id).ifPresent(studentRepository::delete);
    }


    public record DocEntry(MultipartFile file, EDocumentType type) {}
}

