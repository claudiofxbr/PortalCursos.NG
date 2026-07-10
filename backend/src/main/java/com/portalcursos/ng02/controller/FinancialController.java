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
        if (!hasElevatedPrivileges()) {
            return ResponseEntity.status(403)
                .body(new MessageResponse("Acesso negado: use /api/finance/student/{studentId} para consultar seus próprios dados."));
        }
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
        if (!hasElevatedPrivileges()) {
            return ResponseEntity.status(403)
                .body(new MessageResponse("Acesso negado: use /api/finance/student/{studentId} para consultar seus próprios dados."));
        }
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
        if (!hasElevatedPrivileges() && !ownsStudentRecord(studentId)) {
            return ResponseEntity.status(403)
                .body(new MessageResponse("Acesso negado: Você só pode visualizar seus próprios dados financeiros."));
        }
        return ResponseEntity.ok(paymentRepository.findByStudentId(studentId));
    }

    /** true se o usuário autenticado tem role operacional/administrativa (não é um ALUNO comum). */
    private boolean hasElevatedPrivileges() {
        org.springframework.security.core.Authentication auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) return false;
        return auth.getAuthorities().stream()
            .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN")
                        || a.getAuthority().equals("ROLE_ROOT_MASTER")
                        || a.getAuthority().equals("ROLE_FINANCEIRO")
                        || a.getAuthority().equals("ROLE_SECRETARIA"));
    }

    /** true se o usuário autenticado é o próprio aluno (graduação) dono do registro {@code studentId}. */
    private boolean ownsStudentRecord(Long studentId) {
        org.springframework.security.core.Authentication auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        if (!(auth != null && auth.getPrincipal() instanceof com.portalcursos.ng02.service.UserDetailsImpl)) {
            return false;
        }
        com.portalcursos.ng02.service.UserDetailsImpl userDetails = (com.portalcursos.ng02.service.UserDetailsImpl) auth.getPrincipal();
        return studentRepository.findByUserId(userDetails.getId())
            .map(s -> s.getId().equals(studentId))
            .orElse(false);
    }

    /** true se o pagamento pertence ao aluno (graduação ou pós) autenticado. */
    private boolean ownsPayment(Payment payment) {
        org.springframework.security.core.Authentication auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        if (!(auth != null && auth.getPrincipal() instanceof com.portalcursos.ng02.service.UserDetailsImpl)) {
            return false;
        }
        Long userId = ((com.portalcursos.ng02.service.UserDetailsImpl) auth.getPrincipal()).getId();
        if (payment.getStudent() != null) {
            return payment.getStudent().getUser() != null && payment.getStudent().getUser().getId().equals(userId);
        }
        if (payment.getPostgradStudent() != null) {
            return payment.getPostgradStudent().getUser() != null && payment.getPostgradStudent().getUser().getId().equals(userId);
        }
        return false;
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

        if (!hasElevatedPrivileges() && !ownsPayment(p)) {
            return ResponseEntity.status(403)
                .body(new MessageResponse("Acesso negado: esta fatura não pertence a você."));
        }

        p.setMethod(EPaymentMethod.PIX);
        p.setPaymentCode(buildSimulatedPixCode(p));
        paymentRepository.save(p);
        return ResponseEntity.ok(p);
    }

    @PostMapping("/generate-boleto/{paymentId}")
    @PreAuthorize("hasAnyRole('ALUNO', 'ADMIN', 'SECRETARIA', 'FINANCEIRO', 'ROOT_MASTER')")
    public ResponseEntity<?> generateBoleto(@PathVariable @NonNull Long paymentId) {
        Payment p = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new RuntimeException("Fatura não encontrada para gerar boleto"));

        if (!hasElevatedPrivileges() && !ownsPayment(p)) {
            return ResponseEntity.status(403)
                .body(new MessageResponse("Acesso negado: esta fatura não pertence a você."));
        }

        p.setMethod(EPaymentMethod.BOLETO);
        p.setPaymentCode(buildSimulatedBoletoUrl(p));
        paymentRepository.save(p);
        return ResponseEntity.ok(p);
    }

    /**
     * SIMULAÇÃO — não há integração com um PSP (gateway de pagamento) real.
     * O código gerado varia por fatura (id, valor e vencimento) para evitar que
     * todas as cobranças recebam o mesmo "QR Code", mas não é um payload BR Code
     * válido para uso bancário real. Substituir por integração real (ex: Mercado
     * Pago, PagSeguro) antes de processar pagamentos de produção.
     */
    private String buildSimulatedPixCode(Payment p) {
        String amount = p.getTotalAmount() != null ? p.getTotalAmount().toString() : "0.00";
        String txid = String.format("PORTAL%010d", p.getId());
        return "00020126580014BR.GOV.BCB.PIX0136" + txid
                + "5204000053039865802BR5913PortalCursos6008BRASILIA62070503***6304"
                + String.format("%08.2f", Double.parseDouble(amount)).replace(".", "");
    }

    private String buildSimulatedBoletoUrl(Payment p) {
        return "https://portalcursos.edu.br/financeiro/boletos/download/SIM-" + p.getId()
                + "-" + p.getDueDate();
    }
}
