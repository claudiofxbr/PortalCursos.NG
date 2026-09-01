package com.portalcursos.ng02.service;

import com.portalcursos.ng02.model.Payment;
import com.portalcursos.ng02.model.Student;
import com.portalcursos.ng02.model.StudentDocument;
import com.portalcursos.ng02.model.User;
import com.portalcursos.ng02.repository.PaymentRepository;
import com.portalcursos.ng02.repository.StudentRepository;
import com.portalcursos.ng02.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Execução da eliminação definitiva (LGPD art. 18, VI / GDPR art. 17), respeitando prazo
 * legal de guarda antes de anonimizar. Prazos confirmados com o responsável pela aplicação
 * (não inferidos automaticamente): 20 anos para registros acadêmicos, 5 anos para registros
 * financeiros, contados a partir do último evento relevante (última atualização do cadastro
 * do aluno e data de vencimento de cada pagamento, respectivamente — não há campo explícito
 * de "data de conclusão/evasão" no modelo atual).
 *
 * A eliminação anonimiza (mantém a linha, sobrescreve o dado pessoal) em vez de apagar a
 * linha, preservando integridade referencial de histórico/relatórios financeiros e
 * acadêmicos, conforme decisão explícita do responsável pela aplicação.
 */
@Service
@RequiredArgsConstructor
public class DataAnonymizationService {

    private static final Logger logger = LoggerFactory.getLogger(DataAnonymizationService.class);

    private static final int ACADEMIC_RETENTION_YEARS = 20;
    private static final int FINANCIAL_RETENTION_YEARS = 5;

    private final StudentRepository studentRepository;
    private final PaymentRepository paymentRepository;
    private final UserRepository userRepository;
    private final StorageService storageService;

    public record AnonymizationResult(boolean completed, LocalDate earliestEligibleDate, String reason) {
        public static AnonymizationResult ok() {
            return new AnonymizationResult(true, null, null);
        }

        public static AnonymizationResult blocked(LocalDate earliestEligibleDate, String reason) {
            return new AnonymizationResult(false, earliestEligibleDate, reason);
        }
    }

    @Transactional
    public AnonymizationResult anonymize(User user) {
        Optional<Student> studentOpt = studentRepository.findByUserId(user.getId());

        if (studentOpt.isPresent()) {
            Student student = studentOpt.get();
            LocalDate eligibleDate = computeEligibleDate(student);

            if (LocalDate.now().isBefore(eligibleDate)) {
                return AnonymizationResult.blocked(eligibleDate,
                        "Registro acadêmico e/ou financeiro ainda dentro do prazo legal de guarda.");
            }

            anonymizeStudent(student);
        }

        anonymizeUser(user);
        logger.info("[PRIVACY] Dados anonimizados para usuário id={}", user.getId());
        return AnonymizationResult.ok();
    }

    private LocalDate computeEligibleDate(Student student) {
        LocalDate academicReference = student.getUpdatedAt() != null
                ? student.getUpdatedAt().toLocalDate()
                : (student.getCreatedAt() != null ? student.getCreatedAt().toLocalDate() : LocalDate.now());
        LocalDate academicEligible = academicReference.plusYears(ACADEMIC_RETENTION_YEARS);

        List<Payment> payments = paymentRepository.findByStudentId(student.getId());
        LocalDate financialEligible = payments.stream()
                .filter(p -> p.getDueDate() != null)
                .map(p -> p.getDueDate().plusYears(FINANCIAL_RETENTION_YEARS))
                .max(LocalDate::compareTo)
                .orElse(LocalDate.MIN);

        return academicEligible.isAfter(financialEligible) ? academicEligible : financialEligible;
    }

    private void anonymizeStudent(Student student) {
        for (StudentDocument doc : student.getDocuments()) {
            if (doc.getFilePath() != null && !doc.getFilePath().isBlank()) {
                storageService.delete(doc.getFilePath());
            }
        }
        student.getDocuments().clear();

        if (student.getFotoMatricula() != null && !student.getFotoMatricula().isBlank()) {
            storageService.delete(student.getFotoMatricula());
        }

        String anonId = "aluno-anonimizado-" + student.getId();
        student.setFullName("[DADOS ELIMINADOS A PEDIDO DO TITULAR]");
        student.setEmail(anonId + "@anonimizado.local");
        student.setCpf("000.000.000-00");
        student.setPhone(null);
        student.setDateOfBirth(null);
        student.setAddress(null);
        student.setTituloEleitor(null);
        student.setNumeroReservista(null);
        student.setFotoMatricula(null);
        student.setActive(false);
        studentRepository.save(student);
    }

    private void anonymizeUser(User user) {
        String anonId = "usuario-anonimizado-" + user.getId();
        user.setUsername(anonId);
        user.setEmail(anonId + "@anonimizado.local");
        user.setPassword(UUID.randomUUID().toString());
        user.setRoles(new HashSet<>());
        user.setFotoUrl(null);
        userRepository.save(user);
    }
}
