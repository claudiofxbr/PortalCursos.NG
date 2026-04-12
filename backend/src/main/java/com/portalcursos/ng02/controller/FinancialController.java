package com.portalcursos.ng02.controller;

import com.portalcursos.ng02.model.*;
import com.portalcursos.ng02.repository.PaymentRepository;
import com.portalcursos.ng02.repository.PostgradStudentRepository;
import com.portalcursos.ng02.repository.StaffMemberRepository;
import com.portalcursos.ng02.repository.StudentRepository;
import org.springframework.lang.NonNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import com.portalcursos.ng02.dto.MessageResponse;
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
    public ResponseEntity<?> getInvoicesByLevel(@PathVariable String level) {
        try {
            EAcademicLevel academicLevel = EAcademicLevel.valueOf(level.toUpperCase());
            List<Payment> invoices = paymentRepository.findByAcademicLevelAndStatusIn(
                academicLevel, 
                java.util.List.of(EPaymentStatus.PENDING, EPaymentStatus.OVERDUE)
            );
            return ResponseEntity.ok(invoices);
        } catch (Exception e) {
            System.err.println("[SUPREME-ERROR] Erro ao buscar faturas por nível: " + e.getMessage());
            return ResponseEntity.badRequest().body(new MessageResponse("Erro ao buscar faturas: nível inválido ou falha interna."));
        }
    }

    @GetMapping("/history/{level}")
    @PreAuthorize("hasAnyRole('STUDENT', 'ADMIN', 'STAFF')")
    public ResponseEntity<?> getHistoryByLevel(@PathVariable String level) {
        try {
            EAcademicLevel academicLevel = EAcademicLevel.valueOf(level.toUpperCase());
            List<Payment> history = paymentRepository.findByAcademicLevelAndStatus(academicLevel, EPaymentStatus.PAID);
            return ResponseEntity.ok(history);
        } catch (Exception e) {
            System.err.println("[SUPREME-ERROR] Erro ao buscar histórico por nível: " + e.getMessage());
            return ResponseEntity.badRequest().body(new MessageResponse("Erro ao buscar histórico: nível inválido ou falha interna."));
        }
    }

    @PostMapping("/charge")
    @PreAuthorize("hasRole('ADMIN') or hasRole('STAFF')")
    public ResponseEntity<?> createManualCharge(@RequestBody ManualChargeRequest request) {
        try {
            Payment.PaymentBuilder<?, ?> paymentBuilder = Payment.builder()
                    .amount(request.getAmount())
                    .dueDate(request.getDueDate())
                    .status(EPaymentStatus.PENDING)
                    .category(request.getCategory())
                    .secretaryProcessType(request.getSecretaryProcessType())
                    .academicLevel(request.getAcademicLevel())
                    .description(request.getDescription());

            injectAuditStamps(paymentBuilder);

            if (request.getAcademicLevel() == EAcademicLevel.GRADUATION) {
                Optional<Student> student = studentRepository.findById(request.getStudentId());
                if (student.isEmpty()) return ResponseEntity.badRequest().body(new MessageResponse("Estudante de graduação não encontrado"));
                paymentBuilder.student(student.get());
                paymentBuilder.studentPhotoUrl(student.get().getFotoMatricula());
            } else {
                Optional<PostgradStudent> postgradStudent = postgradStudentRepository.findById(request.getStudentId());
                if (postgradStudent.isEmpty()) return ResponseEntity.badRequest().body(new MessageResponse("Estudante de pós-graduação não encontrado"));
                paymentBuilder.postgradStudent(postgradStudent.get());
                paymentBuilder.studentPhotoUrl(postgradStudent.get().getFotoMatricula());
            }

            Payment saved = paymentRepository.save(paymentBuilder.build());
            return ResponseEntity.ok(saved);
        } catch (Exception e) {
            System.err.println("[SUPREME-ERROR] Erro ao criar cobrança manual: " + e.getMessage());
            return ResponseEntity.internalServerError().body(new MessageResponse("Erro ao criar cobrança: " + e.getMessage()));
        }
    }

    @PutMapping("/invoices/{id}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('STAFF')")
    public ResponseEntity<?> updateCharge(@PathVariable Long id, @RequestBody ManualChargeRequest request) {
        try {
            Optional<com.portalcursos.ng02.model.Payment> paymentOpt = paymentRepository.findById(id);
            if (paymentOpt.isEmpty()) {
                return ResponseEntity.status(404).body(new MessageResponse("Cobrança não encontrada"));
            }

            com.portalcursos.ng02.model.Payment payment = paymentOpt.get();
            payment.setAmount(request.getAmount());
            payment.setDueDate(request.getDueDate());
            payment.setCategory(request.getCategory());
            payment.setSecretaryProcessType(request.getSecretaryProcessType());
            payment.setDescription(request.getDescription());

            injectAuditStamps(payment);

            return ResponseEntity.ok(paymentRepository.save(payment));
        } catch (Exception e) {
            System.err.println("[SUPREME-ERROR] Erro ao atualizar cobrança ID " + id + ": " + e.getMessage());
            return ResponseEntity.internalServerError().body(new MessageResponse("Erro ao atualizar cobrança."));
        }
    }

    @DeleteMapping("/invoices/{id}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('STAFF')")
    public ResponseEntity<?> deleteCharge(@PathVariable Long id) {
        try {
            Optional<com.portalcursos.ng02.model.Payment> paymentOpt = paymentRepository.findById(id);
            if (paymentOpt.isEmpty()) {
                return ResponseEntity.status(404).body(new MessageResponse("Cobrança não encontrada"));
            }

            com.portalcursos.ng02.model.Payment payment = paymentOpt.get();
            injectAuditStamps(payment);
            paymentRepository.save(payment);
            paymentRepository.delete(payment);
            return ResponseEntity.ok(new MessageResponse("Cobrança removida com sucesso"));
        } catch (Exception e) {
            System.err.println("[SUPREME-ERROR] Erro ao deletar cobrança ID " + id + ": " + e.getMessage());
            return ResponseEntity.internalServerError().body(new MessageResponse("Erro ao remover cobrança."));
        }
    }

    private void injectAuditStamps(Object p) {
        org.springframework.security.core.Authentication auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof com.portalcursos.ng02.service.UserDetailsImpl) {
            com.portalcursos.ng02.service.UserDetailsImpl userDetails = (com.portalcursos.ng02.service.UserDetailsImpl) auth.getPrincipal();
            staffMemberRepository.findByUserId(userDetails.getId()).ifPresent(staff -> {
                if (p instanceof Payment.PaymentBuilder) {
                    ((Payment.PaymentBuilder<?, ?>) p).creatorName(staff.getFullName());
                    ((Payment.PaymentBuilder<?, ?>) p).creatorPosition(staff.getPosition());
                    ((Payment.PaymentBuilder<?, ?>) p).creatorPhotoUrl(staff.getFotoUrl());
                } else if (p instanceof Payment) {
                    ((Payment) p).setCreatorName(staff.getFullName());
                    ((Payment) p).setCreatorPosition(staff.getPosition());
                    ((Payment) p).setCreatorPhotoUrl(staff.getFotoUrl());
                }
            });
        }
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
    @PreAuthorize("hasRole('STUDENT') or hasRole('ADMIN')")
    public ResponseEntity<?> getStudentPayments(@PathVariable Long studentId) {
        try {
            return ResponseEntity.ok(paymentRepository.findByStudentId(studentId));
        } catch (Exception e) {
            System.err.println("[SUPREME-ERROR] Erro ao buscar pagamentos do aluno ID " + studentId + ": " + e.getMessage());
            return ResponseEntity.internalServerError().body(new MessageResponse("Erro ao carregar pagamentos do aluno."));
        }
    }

    @GetMapping("/invoices")
    @PreAuthorize("hasAnyRole('STUDENT', 'ADMIN', 'STAFF')")
    public ResponseEntity<?> getInvoices() {
        try {
            return ResponseEntity.ok(paymentRepository.findByStatusIn(java.util.List.of(EPaymentStatus.PENDING, EPaymentStatus.OVERDUE)));
        } catch (Exception e) {
            System.err.println("[SUPREME-ERROR] Erro ao listar faturas gerais: " + e.getMessage());
            return ResponseEntity.internalServerError().body(new MessageResponse("Erro ao carregar faturas."));
        }
    }

    @GetMapping("/history")
    @PreAuthorize("hasAnyRole('STUDENT', 'ADMIN', 'STAFF')")
    public ResponseEntity<?> getHistory() {
        try {
            return ResponseEntity.ok(paymentRepository.findByStatus(EPaymentStatus.PAID));
        } catch (Exception e) {
            System.err.println("[SUPREME-ERROR] Erro ao listar histórico geral: " + e.getMessage());
            return ResponseEntity.internalServerError().body(new MessageResponse("Erro ao carregar histórico."));
        }
    }

    @PostMapping("/generate-pix/{paymentId}")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<?> generatePix(@PathVariable @NonNull Long paymentId) {
        try {
            Optional<Payment> payment = paymentRepository.findById(paymentId);
            if (payment.isPresent()) {
                Payment p = payment.get();
                p.setMethod(EPaymentMethod.PIX);
                p.setPaymentCode("00020126580014BR.GOV.BCB.PIX0136123e4567-e89b-12d3-a456-4266141740005204000053039865802BR5913PortalCursos6008BRASILIA62070503***6304");
                paymentRepository.save(p);
                return ResponseEntity.ok(p);
            }
            return ResponseEntity.status(404).body(new MessageResponse("Fatura não encontrada para gerar PIX"));
        } catch (Exception e) {
            System.err.println("[SUPREME-ERROR] Erro ao gerar PIX para fatura ID " + paymentId + ": " + e.getMessage());
            return ResponseEntity.internalServerError().body(new MessageResponse("Erro ao processar PIX."));
        }
    }

    @PostMapping("/generate-boleto/{paymentId}")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<?> generateBoleto(@PathVariable @NonNull Long paymentId) {
        try {
            Optional<Payment> payment = paymentRepository.findById(paymentId);
            if (payment.isPresent()) {
                Payment p = payment.get();
                p.setMethod(EPaymentMethod.BOLETO);
                p.setPaymentCode("https://portalcursos.edu.br/financeiro/boletos/download/B123456789");
                paymentRepository.save(p);
                return ResponseEntity.ok(p);
            }
            return ResponseEntity.status(404).body(new MessageResponse("Fatura não encontrada para gerar boleto"));
        } catch (Exception e) {
            System.err.println("[SUPREME-ERROR] Erro ao gerar boleto para fatura ID " + paymentId + ": " + e.getMessage());
            return ResponseEntity.internalServerError().body(new MessageResponse("Erro ao processar boleto."));
        }
    }
}
