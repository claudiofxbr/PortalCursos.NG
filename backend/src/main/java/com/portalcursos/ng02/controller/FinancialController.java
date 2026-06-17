package com.portalcursos.ng02.controller;

import com.portalcursos.ng02.model.*;
import com.portalcursos.ng02.repository.PaymentRepository;
import com.portalcursos.ng02.repository.PostgradStudentRepository;
import com.portalcursos.ng02.repository.StudentRepository;
import com.portalcursos.ng02.service.AuditService;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.NonNull;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import com.portalcursos.ng02.dto.MessageResponse;
import java.util.List;

@RestController
@RequestMapping("/api/finance")
@RequiredArgsConstructor
public class FinancialController {

    private final PaymentRepository paymentRepository;
    private final StudentRepository studentRepository;
    private final PostgradStudentRepository postgradStudentRepository;
    private final AuditService auditService;

    @GetMapping("/invoices/{level}")
    @PreAuthorize("hasAnyRole('ALUNO', 'ADMIN', 'SECRETARIA', 'FINANCEIRO', 'ROOT_MASTER')")
    public ResponseEntity<?> getInvoicesByLevel(@PathVariable String level) {
        EAcademicLevel academicLevel = EAcademicLevel.valueOf(level.toUpperCase());
        List<Payment> invoices = paymentRepository.findByAcademicLevelAndStatusIn(
            academicLevel, 
            java.util.List.of(EPaymentStatus.PENDING, EPaymentStatus.OVERDUE)
        );
        return ResponseEntity.ok(invoices);
    }

    @GetMapping("/history/{level}")
    @PreAuthorize("hasAnyRole('ALUNO', 'ADMIN', 'SECRETARIA', 'FINANCEIRO', 'ROOT_MASTER')")
    public ResponseEntity<?> getHistoryByLevel(@PathVariable String level) {
        EAcademicLevel academicLevel = EAcademicLevel.valueOf(level.toUpperCase());
        List<Payment> history = paymentRepository.findByAcademicLevelAndStatus(academicLevel, EPaymentStatus.PAID);
        return ResponseEntity.ok(history);
    }

    @PostMapping("/charge")
    @PreAuthorize("hasAnyRole('ADMIN', 'SECRETARIA', 'FINANCEIRO', 'ROOT_MASTER')")
    public ResponseEntity<?> createManualCharge(@RequestBody ManualChargeRequest request) {
        Payment payment = Payment.builder()
                .amount(request.getAmount())
                .dueDate(request.getDueDate())
                .status(EPaymentStatus.PENDING)
                .category(request.getCategory())
                .secretaryProcessType(request.getSecretaryProcessType())
                .academicLevel(request.getAcademicLevel())
                .description(request.getDescription())
                .build();

        if (request.getAcademicLevel() == EAcademicLevel.GRADUATION) {
            Student student = studentRepository.findById(request.getStudentId())
                    .orElseThrow(() -> new RuntimeException("Estudante de graduação não encontrado"));
            payment.setStudent(student);
            payment.setStudentPhotoUrl(student.getFotoMatricula());
        } else {
            PostgradStudent postgradStudent = postgradStudentRepository.findById(request.getStudentId())
                    .orElseThrow(() -> new RuntimeException("Estudante de pós-graduação não encontrado"));
            payment.setPostgradStudent(postgradStudent);
            payment.setStudentPhotoUrl(postgradStudent.getFotoMatricula());
        }

        auditService.injectCreator(payment);
        return ResponseEntity.ok(paymentRepository.save(payment));
    }

    @PutMapping("/invoices/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SECRETARIA', 'FINANCEIRO', 'ROOT_MASTER')")
    public ResponseEntity<?> updateCharge(@PathVariable Long id, @RequestBody ManualChargeRequest request) {
        Payment payment = paymentRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Cobrança não encontrada"));

        payment.setAmount(request.getAmount());
        payment.setDueDate(request.getDueDate());
        payment.setCategory(request.getCategory());
        payment.setSecretaryProcessType(request.getSecretaryProcessType());
        payment.setDescription(request.getDescription());

        auditService.injectCreator(payment);
        return ResponseEntity.ok(paymentRepository.save(payment));
    }

    @DeleteMapping("/invoices/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SECRETARIA', 'FINANCEIRO', 'ROOT_MASTER')")
    public ResponseEntity<?> deleteCharge(@PathVariable Long id) {
        Payment payment = paymentRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Cobrança não encontrada"));

        paymentRepository.delete(payment);
        return ResponseEntity.ok(new MessageResponse("Cobrança removida com sucesso"));
    }

    public static class ManualChargeRequest {
        private java.math.BigDecimal amount;
        private java.time.LocalDate dueDate;
        private Long studentId;
        private EAcademicLevel academicLevel;
        private EPaymentCategory category;
        private ESecretaryProcessType secretaryProcessType;
        private String description;

        public java.math.BigDecimal getAmount() { return amount; }
        public void setAmount(java.math.BigDecimal amount) { this.amount = amount; }
        public java.time.LocalDate getDueDate() { return dueDate; }
        public void setDueDate(java.time.LocalDate dueDate) { this.dueDate = dueDate; }
        public Long getStudentId() { return studentId; }
        public void setStudentId(Long studentId) { this.studentId = studentId; }
        public EAcademicLevel getAcademicLevel() { return academicLevel; }
        public void setAcademicLevel(EAcademicLevel academicLevel) { this.academicLevel = academicLevel; }
        public EPaymentCategory getCategory() { return category; }
        public void setCategory(EPaymentCategory category) { this.category = category; }
        public ESecretaryProcessType getSecretaryProcessType() { return secretaryProcessType; }
        public void setSecretaryProcessType(ESecretaryProcessType secretaryProcessType) { this.secretaryProcessType = secretaryProcessType; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
    }

    @GetMapping("/student/{studentId}")
    @PreAuthorize("hasAnyRole('ALUNO', 'ADMIN', 'SECRETARIA', 'FINANCEIRO', 'ROOT_MASTER')")
    public ResponseEntity<?> getStudentPayments(@PathVariable Long studentId) {
        org.springframework.security.core.Authentication auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof com.portalcursos.ng02.service.UserDetailsImpl) {
            com.portalcursos.ng02.service.UserDetailsImpl userDetails = (com.portalcursos.ng02.service.UserDetailsImpl) auth.getPrincipal();
            
            boolean hasElevatedPrivileges = auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN") 
                            || a.getAuthority().equals("ROLE_ROOT_MASTER") 
                            || a.getAuthority().equals("ROLE_FINANCEIRO") 
                            || a.getAuthority().equals("ROLE_SECRETARIA"));
            
            if (!hasElevatedPrivileges) {
                java.util.Optional<com.portalcursos.ng02.model.Student> studentOpt = studentRepository.findByUserId(userDetails.getId());
                if (studentOpt.isEmpty() || !studentOpt.get().getId().equals(studentId)) {
                    return ResponseEntity.status(403)
                        .body(new MessageResponse("Acesso negado: Você só pode visualizar seus próprios dados financeiros."));
                }
            }
        } else {
            return ResponseEntity.status(401)
                .body(new MessageResponse("Acesso negado: Usuário não autenticado."));
        }
        return ResponseEntity.ok(paymentRepository.findByStudentId(studentId));
    }

    @GetMapping("/invoices")
    @PreAuthorize("hasAnyRole('ADMIN', 'SECRETARIA', 'FINANCEIRO', 'ROOT_MASTER')")
    public ResponseEntity<?> getInvoices() {
        return ResponseEntity.ok(paymentRepository.findByStatusIn(java.util.List.of(EPaymentStatus.PENDING, EPaymentStatus.OVERDUE)));
    }

    @GetMapping("/history")
    @PreAuthorize("hasAnyRole('ADMIN', 'SECRETARIA', 'FINANCEIRO', 'ROOT_MASTER')")
    public ResponseEntity<?> getHistory() {
        return ResponseEntity.ok(paymentRepository.findByStatus(EPaymentStatus.PAID));
    }

    @PostMapping("/generate-pix/{paymentId}")
    @PreAuthorize("hasAnyRole('ALUNO', 'ADMIN', 'SECRETARIA', 'FINANCEIRO', 'ROOT_MASTER')")
    public ResponseEntity<?> generatePix(@PathVariable @NonNull Long paymentId) {
        Payment p = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new RuntimeException("Fatura não encontrada para gerar PIX"));
        
        p.setMethod(EPaymentMethod.PIX);
        p.setPaymentCode("00020126580014BR.GOV.BCB.PIX0136123e4567-e89b-12d3-a456-4266141740005204000053039865802BR5913PortalCursos6008BRASILIA62070503***6304");
        paymentRepository.save(p);
        return ResponseEntity.ok(p);
    }

    @PostMapping("/generate-boleto/{paymentId}")
    @PreAuthorize("hasAnyRole('ALUNO', 'ADMIN', 'SECRETARIA', 'FINANCEIRO', 'ROOT_MASTER')")
    public ResponseEntity<?> generateBoleto(@PathVariable @NonNull Long paymentId) {
        Payment p = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new RuntimeException("Fatura não encontrada para gerar boleto"));

        p.setMethod(EPaymentMethod.BOLETO);
        p.setPaymentCode("https://portalcursos.edu.br/financeiro/boletos/download/B123456789");
        paymentRepository.save(p);
        return ResponseEntity.ok(p);
    }
}
