package com.portalcursos.ng02.service;

import com.portalcursos.ng02.dto.ManualChargeRequest;
import com.portalcursos.ng02.exception.ResourceNotFoundException;
import com.portalcursos.ng02.model.EAcademicLevel;
import com.portalcursos.ng02.model.EPaymentStatus;
import com.portalcursos.ng02.model.Payment;
import com.portalcursos.ng02.model.Student;
import com.portalcursos.ng02.repository.PaymentRepository;
import com.portalcursos.ng02.repository.PostgradStudentRepository;
import com.portalcursos.ng02.repository.StudentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * CRUD de cobranças manuais — extraído de {@code FinancialController} (Lote E, item F2 da
 * auditoria). Puro código movido, sem mudança de comportamento: mesma lógica, mesmas
 * mensagens de erro, só fora do controller.
 */
@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final StudentRepository studentRepository;
    private final PostgradStudentRepository postgradStudentRepository;
    private final AuditService auditService;
    private final ChargeCatalogService chargeCatalogService;

    public Payment createManualCharge(ManualChargeRequest request) {
        Payment payment = Payment.builder()
                .amount(request.getAmount())
                .dueDate(request.getDueDate())
                .status(EPaymentStatus.PENDING)
                .category(request.getCategory())
                .secretaryProcessType(request.getSecretaryProcessType())
                .academicLevel(request.getAcademicLevel())
                .description(request.getDescription())
                .build();

        Student student;
        if (request.getAcademicLevel() == EAcademicLevel.GRADUATION) {
            student = studentRepository.findById(request.getStudentId())
                    .orElseThrow(() -> new ResourceNotFoundException("Estudante de graduação não encontrado"));
        } else {
            student = postgradStudentRepository.findById(request.getStudentId())
                    .orElseThrow(() -> new ResourceNotFoundException("Estudante de pós-graduação não encontrado"));
        }
        payment.setStudent(student);
        payment.setStudentPhotoUrl(student.getFotoMatricula());

        // Procedimento escolhido na tabela de valores: o servidor decide o valor (o amount enviado é ignorado).
        if (request.getChargePriceId() != null) {
            ChargeCatalogService.Resolved resolved =
                    chargeCatalogService.resolve(request.getChargePriceId(), student, request.getAcademicLevel());
            payment.setAmount(resolved.value());
            payment.setChargePriceId(resolved.price().getId());
            payment.setCategory(resolved.category());
            payment.setSecretaryProcessType(resolved.processType());
            if (payment.getDescription() == null || payment.getDescription().isBlank()) {
                payment.setDescription(resolved.price().getName());
            }
        }

        auditService.injectCreator(payment);
        return paymentRepository.save(payment);
    }

    public Payment updateCharge(Long id, ManualChargeRequest request) {
        Payment payment = paymentRepository.findByIdWithCreatorAndStudent(id)
                .orElseThrow(() -> new ResourceNotFoundException("Cobrança não encontrada"));

        payment.setAmount(request.getAmount());
        payment.setDueDate(request.getDueDate());
        payment.setCategory(request.getCategory());
        payment.setSecretaryProcessType(request.getSecretaryProcessType());
        payment.setDescription(request.getDescription());

        auditService.injectCreator(payment);
        return paymentRepository.save(payment);
    }

    public void deleteCharge(Long id) {
        Payment payment = paymentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Cobrança não encontrada"));

        // Soft-delete explícito (mesmo padrão de UserService.deleteUser) — não usa
        // paymentRepository.delete()/deleteById(), que dependia do @SQLDelete removido.
        payment.setActive(false);
        paymentRepository.save(payment);
    }
}
