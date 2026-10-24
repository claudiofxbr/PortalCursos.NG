package com.portalcursos.ng02.controller;

import com.portalcursos.ng02.model.*;
import com.portalcursos.ng02.repository.PaymentRepository;
import com.portalcursos.ng02.service.CardCheckoutRateLimiter;
import com.portalcursos.ng02.service.CardCheckoutService;
import com.portalcursos.ng02.service.PagBankEnvironmentPolicy;
import com.portalcursos.ng02.service.PaymentAuthorizationService;
import com.portalcursos.ng02.service.PaymentCodeGenerationService;
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
import com.portalcursos.ng02.dto.PaymentView;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import java.util.List;

@RestController
@RequestMapping("/api/finance")
@RequiredArgsConstructor
public class FinancialController {

    private final PaymentRepository paymentRepository;
    private final PaymentAuthorizationService authorizationService;
    private final PaymentService paymentService;
    private final PaymentCodeGenerationService paymentCodeGenerationService;
    private final CardCheckoutService cardCheckoutService;
    private final CardCheckoutRateLimiter cardCheckoutRateLimiter;
    private final PagBankEnvironmentPolicy environmentPolicy;

    @GetMapping("/invoices/{level}")
    @PreAuthorize("hasAnyRole('ALUNO', 'ADMIN', 'SECRETARIA', 'FINANCEIRO', 'ROOT_MASTER')")
    public ResponseEntity<?> getInvoicesByLevel(@PathVariable String level,
            @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size) {
        if (!authorizationService.hasElevatedPrivileges()) {
            return ResponseEntity.status(403)
                .body(new MessageResponse("Acesso negado: use /api/finance/student/{studentId} para consultar seus próprios dados."));
        }
        EAcademicLevel academicLevel = parseAcademicLevel(level);
        Pageable pageable = optionalPageable(page, size);
        if (pageable != null) {
            return pagedResponse(paymentRepository.findByAcademicLevelAndStatusIn(
                academicLevel, java.util.List.of(EPaymentStatus.PENDING, EPaymentStatus.OVERDUE), pageable));
        }
        List<Payment> invoices = paymentRepository.findByAcademicLevelAndStatusIn(
            academicLevel,
            java.util.List.of(EPaymentStatus.PENDING, EPaymentStatus.OVERDUE)
        );
        return ResponseEntity.ok(invoices);
    }

    @GetMapping("/history/{level}")
    @PreAuthorize("hasAnyRole('ALUNO', 'ADMIN', 'SECRETARIA', 'FINANCEIRO', 'ROOT_MASTER')")
    public ResponseEntity<?> getHistoryByLevel(@PathVariable String level,
            @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size) {
        if (!authorizationService.hasElevatedPrivileges()) {
            return ResponseEntity.status(403)
                .body(new MessageResponse("Acesso negado: use /api/finance/student/{studentId} para consultar seus próprios dados."));
        }
        EAcademicLevel academicLevel = parseAcademicLevel(level);
        Pageable pageable = optionalPageable(page, size);
        if (pageable != null) {
            return pagedResponse(paymentRepository.findByAcademicLevelAndStatus(academicLevel, EPaymentStatus.PAID, pageable));
        }
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
    @PreAuthorize("hasAnyRole('ADMIN', 'ROOT_MASTER')")
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
        // O aluno também chega aqui: só PaymentView, nunca a entidade (ids do PSP, versão, auditoria).
        return ResponseEntity.ok(paymentRepository.findByStudentId(studentId).stream().map(PaymentView::from).toList());
    }

    private static final int MAX_PAGE_SIZE = 200;

    /**
     * Paginação opt-in: sem {@code page} nem {@code size} devolve null e a listagem segue completa
     * (contrato atual da tela financeira). Com qualquer um dos dois, pagina (mais recentes primeiro).
     */
    private Pageable optionalPageable(Integer page, Integer size) {
        if (page == null && size == null) return null;
        int p = page == null ? 0 : page;
        int s = size == null ? 50 : size;
        if (p < 0 || s < 1 || s > MAX_PAGE_SIZE) {
            throw new BusinessException("Paginação inválida: page >= 0 e size entre 1 e " + MAX_PAGE_SIZE);
        }
        return PageRequest.of(p, s, Sort.by(Sort.Direction.DESC, "id"));
    }

    private ResponseEntity<List<Payment>> pagedResponse(org.springframework.data.domain.Page<Payment> result) {
        return ResponseEntity.ok().header("X-Total-Count", String.valueOf(result.getTotalElements())).body(result.getContent());
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
    public ResponseEntity<?> getInvoices(@RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size) {
        Pageable pageable = optionalPageable(page, size);
        if (pageable != null) {
            return pagedResponse(paymentRepository.findByStatusIn(java.util.List.of(EPaymentStatus.PENDING, EPaymentStatus.OVERDUE), pageable));
        }
        return ResponseEntity.ok(paymentRepository.findByStatusIn(java.util.List.of(EPaymentStatus.PENDING, EPaymentStatus.OVERDUE)));
    }

    @GetMapping("/history")
    @PreAuthorize("hasAnyRole('ADMIN', 'SECRETARIA', 'FINANCEIRO', 'ROOT_MASTER')")
    public ResponseEntity<?> getHistory(@RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size) {
        Pageable pageable = optionalPageable(page, size);
        if (pageable != null) {
            return pagedResponse(paymentRepository.findByStatus(EPaymentStatus.PAID, pageable));
        }
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

        return ResponseEntity.ok(paymentCodeGenerationService.generatePix(p));
    }

    /**
     * Cria (ou reaproveita) o checkout hospedado de cartão da fatura. Não devolve "aprovado": a baixa
     * só ocorre pelo webhook. Fora de produção só administrador/testador paga (cartão de teste é público).
     */
    @PostMapping("/generate-card-checkout/{paymentId}")
    @PreAuthorize("hasAnyRole('ALUNO', 'ADMIN', 'SECRETARIA', 'FINANCEIRO', 'ROOT_MASTER')")
    public ResponseEntity<?> generateCardCheckout(@PathVariable @NonNull Long paymentId) {
        Payment p = paymentRepository.findByIdWithCreatorAndStudent(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException("Fatura não encontrada para pagamento por cartão"));

        boolean elevated = authorizationService.hasElevatedPrivileges();
        if (!elevated && !authorizationService.ownsPayment(p)) {
            return ResponseEntity.status(403)
                .body(new MessageResponse("Acesso negado: esta fatura não pertence a você."));
        }

        if (!environmentPolicy.canPay(elevated, authorizationService.currentUserEmail())) {
            return ResponseEntity.status(403)
                .body(new MessageResponse("Pagamento por cartão em breve."));
        }

        if (!cardCheckoutRateLimiter.tryAcquire(authorizationService.currentUserKey())) {
            return ResponseEntity.status(429)
                .header("Retry-After", "600")
                .body(new MessageResponse("Muitas tentativas de pagamento. Aguarde alguns minutos."));
        }

        return ResponseEntity.ok(cardCheckoutService.generate(p));
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

        return ResponseEntity.ok(PaymentView.from(paymentCodeGenerationService.generateBoleto(p)));
    }
}
