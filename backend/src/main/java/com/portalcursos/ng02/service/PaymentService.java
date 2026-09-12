package com.portalcursos.ng02.service;

import com.portalcursos.ng02.dto.ManualChargeRequest;
import com.portalcursos.ng02.exception.ResourceNotFoundException;
import com.portalcursos.ng02.model.EAcademicLevel;
import com.portalcursos.ng02.model.EPaymentStatus;
import com.portalcursos.ng02.model.Payment;
import com.portalcursos.ng02.model.PostgradStudent;
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

        if (request.getAcademicLevel() == EAcademicLevel.GRADUATION) {
            Student student = studentRepository.findById(request.getStudentId())
                    .orElseThrow(() -> new ResourceNotFoundException("Estudante de graduação não encontrado"));
            payment.setStudent(student);
            payment.setStudentPhotoUrl(student.getFotoMatricula());
        } else {
            PostgradStudent postgradStudent = postgradStudentRepository.findById(request.getStudentId())
                    .orElseThrow(() -> new ResourceNotFoundException("Estudante de pós-graduação não encontrado"));
            payment.setStudent(postgradStudent);
            payment.setStudentPhotoUrl(postgradStudent.getFotoMatricula());
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

        paymentRepository.delete(payment);
    }
}
