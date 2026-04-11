package com.portalcursos.ng02.controller;

import com.portalcursos.ng02.model.Payment;
import com.portalcursos.ng02.model.PostgradStudent;
import com.portalcursos.ng02.model.Student;
import com.portalcursos.ng02.repository.PaymentRepository;
import com.portalcursos.ng02.repository.PostgradStudentRepository;
import com.portalcursos.ng02.repository.StaffMemberRepository;
import com.portalcursos.ng02.repository.StudentRepository;
import org.springframework.lang.NonNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

@CrossOrigin(origins = "*", maxAge = 3600)
@RestController
@RequestMapping("/api/finance")
public class FinancialController {

    @Autowired
    PaymentRepository paymentRepository;

    @Autowired
    StaffMemberRepository staffMemberRepository;

    @Autowired
    com.portalcursos.ng02.repository.UserRepository userRepository;

    @Autowired
    StudentRepository studentRepository;

    @Autowired
    PostgradStudentRepository postgradStudentRepository;

    @GetMapping("/invoices/{level}")
    @PreAuthorize("hasAnyRole('STUDENT', 'ADMIN', 'STAFF')")
    public List<Payment> getInvoicesByLevel(@PathVariable String level) {
        Payment.EAcademicLevel academicLevel = Payment.EAcademicLevel.valueOf(level.toUpperCase());
        return paymentRepository.findByAcademicLevelAndStatusIn(
            academicLevel, 
            java.util.List.of(Payment.EPaymentStatus.PENDING, Payment.EPaymentStatus.OVERDUE)
        );
    }

    @GetMapping("/history/{level}")
    @PreAuthorize("hasAnyRole('STUDENT', 'ADMIN', 'STAFF')")
    public List<Payment> getHistoryByLevel(@PathVariable String level) {
        Payment.EAcademicLevel academicLevel = Payment.EAcademicLevel.valueOf(level.toUpperCase());
        return paymentRepository.findByAcademicLevelAndStatus(academicLevel, Payment.EPaymentStatus.PAID);
    }

    @PostMapping("/charge")
    @PreAuthorize("hasRole('ADMIN') or hasRole('STAFF')")
    public ResponseEntity<?> createManualCharge(@RequestBody ManualChargeRequest request) {
        Payment.PaymentBuilder paymentBuilder = Payment.builder()
                .amount(request.getAmount())
                .dueDate(request.getDueDate())
                .status(Payment.EPaymentStatus.PENDING)
                .category(request.getCategory())
                .secretaryProcessType(request.getSecretaryProcessType())
                .academicLevel(request.getAcademicLevel())
                .description(request.getDescription());

        // Injetar dados do emissor (Usuário logado)
        org.springframework.security.core.Authentication auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof com.portalcursos.ng02.service.UserDetailsImpl) {
            com.portalcursos.ng02.service.UserDetailsImpl userDetails = (com.portalcursos.ng02.service.UserDetailsImpl) auth.getPrincipal();
            java.util.Optional<com.portalcursos.ng02.model.StaffMember> staff = staffMemberRepository.findByUserId(userDetails.getId());
            if (staff.isPresent()) {
                paymentBuilder.creatorName(staff.get().getFullName());
                paymentBuilder.creatorPosition(staff.get().getPosition());
                paymentBuilder.creatorPhotoUrl(staff.get().getFotoUrl());
            }
        }

        if (request.getAcademicLevel() == Payment.EAcademicLevel.GRADUATION) {
            Optional<Student> student = studentRepository.findById(request.getStudentId());
            if (student.isEmpty()) return ResponseEntity.badRequest().body("Estudante de graduação não encontrado");
            paymentBuilder.student(student.get());
            paymentBuilder.studentPhotoUrl(student.get().getFotoUrl());
        } else {
            Optional<PostgradStudent> postgradStudent = postgradStudentRepository.findById(request.getStudentId());
            if (postgradStudent.isEmpty()) return ResponseEntity.badRequest().body("Estudante de pós-graduação não encontrado");
            paymentBuilder.postgradStudent(postgradStudent.get());
            paymentBuilder.studentPhotoUrl(postgradStudent.get().getFotoUrl());
        }

        Payment saved = paymentRepository.save(paymentBuilder.build());
        return ResponseEntity.ok(saved);
    }

    // DTO estático para a requisição
    public static class ManualChargeRequest {
        private java.math.BigDecimal amount;
        private java.time.LocalDate dueDate;
        private Long studentId;
        private Payment.EAcademicLevel academicLevel;
        private Payment.EPaymentCategory category;
        private Payment.ESecretaryProcessType secretaryProcessType;
        private String description;

        // Getters e Setters
        public java.math.BigDecimal getAmount() { return amount; }
        public void setAmount(java.math.BigDecimal amount) { this.amount = amount; }
        public java.time.LocalDate getDueDate() { return dueDate; }
        public void setDueDate(java.time.LocalDate dueDate) { this.dueDate = dueDate; }
        public Long getStudentId() { return studentId; }
        public void setStudentId(Long studentId) { this.studentId = studentId; }
        public Payment.EAcademicLevel getAcademicLevel() { return academicLevel; }
        public void setAcademicLevel(Payment.EAcademicLevel academicLevel) { this.academicLevel = academicLevel; }
        public Payment.EPaymentCategory getCategory() { return category; }
        public void setCategory(Payment.EPaymentCategory category) { this.category = category; }
        public Payment.ESecretaryProcessType getSecretaryProcessType() { return secretaryProcessType; }
        public void setSecretaryProcessType(Payment.ESecretaryProcessType secretaryProcessType) { this.secretaryProcessType = secretaryProcessType; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
    }

    @GetMapping("/student/{studentId}")
    @PreAuthorize("hasRole('STUDENT') or hasRole('ADMIN')")
    public List<Payment> getStudentPayments(@PathVariable Long studentId) {
        return paymentRepository.findByStudentId(studentId);
    }

    @GetMapping("/invoices")
    @PreAuthorize("hasAnyRole('STUDENT', 'ADMIN', 'STAFF')")
    public List<Payment> getInvoices() {
        return paymentRepository.findByStatusIn(java.util.List.of(Payment.EPaymentStatus.PENDING, Payment.EPaymentStatus.OVERDUE));
    }

    @GetMapping("/history")
    @PreAuthorize("hasAnyRole('STUDENT', 'ADMIN', 'STAFF')")
    public List<Payment> getHistory() {
        return paymentRepository.findByStatus(Payment.EPaymentStatus.PAID);
    }

    @PostMapping("/generate-pix/{paymentId}")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<?> generatePix(@PathVariable @NonNull Long paymentId) {
        Optional<Payment> payment = paymentRepository.findById(paymentId);
        if (payment.isPresent()) {
            Payment p = payment.get();
            p.setMethod(Payment.EPaymentMethod.PIX);
            p.setPaymentCode("00020126580014BR.GOV.BCB.PIX0136123e4567-e89b-12d3-a456-4266141740005204000053039865802BR5913PortalCursos6008BRASILIA62070503***6304");
            paymentRepository.save(p);
            return ResponseEntity.ok(p);
        }
        return ResponseEntity.notFound().build();
    }

    @PostMapping("/generate-boleto/{paymentId}")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<?> generateBoleto(@PathVariable @NonNull Long paymentId) {
        Optional<Payment> payment = paymentRepository.findById(paymentId);
        if (payment.isPresent()) {
            Payment p = payment.get();
            p.setMethod(Payment.EPaymentMethod.BOLETO);
            p.setPaymentCode("https://portalcursos.edu.br/financeiro/boletos/download/B123456789");
            paymentRepository.save(p);
            return ResponseEntity.ok(p);
        }
        return ResponseEntity.notFound().build();
    }
}
