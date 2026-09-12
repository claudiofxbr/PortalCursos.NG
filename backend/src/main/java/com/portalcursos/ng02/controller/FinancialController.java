package com.portalcursos.ng02.controller;

import com.portalcursos.ng02.model.*;
import com.portalcursos.ng02.repository.PaymentRepository;
import com.portalcursos.ng02.service.PaymentAuthorizationService;
import com.portalcursos.ng02.service.PaymentService;
import com.portalcursos.ng02.exception.ResourceNotFoundException;
import com.portalcursos.ng02.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.NonNull;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import com.portalcursos.ng02.dto.ManualChargeRequest;
import com.portalcursos.ng02.dto.MessageResponse;
import java.util.List;

@RestController
@RequestMapping("/api/finance")
@RequiredArgsConstructor
public class FinancialController {

    private final PaymentRepository paymentRepository;
    private final PaymentAuthorizationService authorizationService;
    private final PaymentService paymentService;

    @GetMapping("/invoices/{level}")
    @PreAuthorize("hasAnyRole('ALUNO', 'ADMIN', 'SECRETARIA', 'FINANCEIRO', 'ROOT_MASTER')")
    public ResponseEntity<?> getInvoicesByLevel(@PathVariable String level) {
        if (!authorizationService.hasElevatedPrivileges()) {
            return ResponseEntity.status(403)
                .body(new MessageResponse("Acesso negado: use /api/finance/student/{studentId} para consultar seus próprios dados."));
        }
        EAcademicLevel academicLevel = parseAcademicLevel(level);
        List<Payment> invoices = paymentRepository.findByAcademicLevelAndStatusIn(
            academicLevel,
            java.util.List.of(EPaymentStatus.PENDING, EPaymentStatus.OVERDUE)
        );
        return ResponseEntity.ok(invoices);
    }

    @GetMapping("/history/{level}")
    @PreAuthorize("hasAnyRole('ALUNO', 'ADMIN', 'SECRETARIA', 'FINANCEIRO', 'ROOT_MASTER')")
    public ResponseEntity<?> getHistoryByLevel(@PathVariable String level) {
        if (!authorizationService.hasElevatedPrivileges()) {
            return ResponseEntity.status(403)
                .body(new MessageResponse("Acesso negado: use /api/finance/student/{studentId} para consultar seus próprios dados."));
        }
        EAcademicLevel academicLevel = parseAcademicLevel(level);
        List<Payment> history = paymentRepository.findByAcademicLevelAndStatus(academicLevel, EPaymentStatus.PAID);
        return ResponseEntity.ok(history);
    }

    @PostMapping("/charge")
    @PreAuthorize("hasAnyRole('ADMIN', 'SECRETARIA', 'FINANCEIRO', 'ROOT_MASTER')")
    public ResponseEntity<?> createManualCharge(@RequestBody ManualChargeRequest request) {
        return ResponseEntity.ok(paymentService.createManualCharge(request));
    }

    @PutMapping("/invoices/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SECRETARIA', 'FINANCEIRO', 'ROOT_MASTER')")
    public ResponseEntity<?> updateCharge(@PathVariable Long id, @RequestBody ManualChargeRequest request) {
        return ResponseEntity.ok(paymentService.updateCharge(id, request));
    }

    @DeleteMapping("/invoices/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SECRETARIA', 'FINANCEIRO', 'ROOT_MASTER')")
    public ResponseEntity<?> deleteCharge(@PathVariable Long id) {
        paymentService.deleteCharge(id);
        return ResponseEntity.ok(new MessageResponse("Cobrança removida com sucesso"));
    }

    @GetMapping("/student/{studentId}")
    @PreAuthorize("hasAnyRole('ALUNO', 'ADMIN', 'SECRETARIA', 'FINANCEIRO', 'ROOT_MASTER')")
    public ResponseEntity<?> getStudentPayments(@PathVariable Long studentId) {
        if (!authorizationService.hasElevatedPrivileges() && !authorizationService.ownsStudentRecord(studentId)) {
            return ResponseEntity.status(403)
                .body(new MessageResponse("Acesso negado: Você só pode visualizar seus próprios dados financeiros."));
        }
        return ResponseEntity.ok(paymentRepository.findByStudentId(studentId));
    }

    /** Converte o parâmetro de rota em {@link EAcademicLevel}, traduzindo valor inválido em HTTP 400. */
    private EAcademicLevel parseAcademicLevel(String level) {
        try {
            return EAcademicLevel.valueOf(level.toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new BusinessException("Nível acadêmico inválido: " + level);
        }
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
        Payment p = paymentRepository.findByIdWithCreatorAndStudent(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException("Fatura não encontrada para gerar PIX"));

        if (!authorizationService.hasElevatedPrivileges() && !authorizationService.ownsPayment(p)) {
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
        Payment p = paymentRepository.findByIdWithCreatorAndStudent(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException("Fatura não encontrada para gerar boleto"));

        if (!authorizationService.hasElevatedPrivileges() && !authorizationService.ownsPayment(p)) {
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
        java.math.BigDecimal amount = p.getTotalAmount() != null ? p.getTotalAmount() : java.math.BigDecimal.ZERO;
        String amountDigits = amount.setScale(2, java.math.RoundingMode.HALF_UP)
                .movePointRight(2)
                .toBigInteger()
                .toString();
        String txid = String.format("PORTAL%010d", p.getId());
        return "00020126580014BR.GOV.BCB.PIX0136" + txid
                + "5204000053039865802BR5913PortalCursos6008BRASILIA62070503***6304"
                + String.format("%08s", amountDigits).replace(" ", "0");
    }

    private String buildSimulatedBoletoUrl(Payment p) {
        return "https://portalcursos.edu.br/financeiro/boletos/download/SIM-" + p.getId()
                + "-" + p.getDueDate();
    }
}
