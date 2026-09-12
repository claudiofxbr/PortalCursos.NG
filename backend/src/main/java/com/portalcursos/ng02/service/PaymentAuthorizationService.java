package com.portalcursos.ng02.service;

import com.portalcursos.ng02.model.Payment;
import com.portalcursos.ng02.repository.StudentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

/**
 * Regras de autorização do módulo financeiro — extraídas de {@code FinancialController}
 * (Lote E, item F1 da auditoria). Puro código movido, sem mudança de comportamento:
 * mesma lógica, mesmas assinaturas, só fora do controller.
 */
@Service
@RequiredArgsConstructor
public class PaymentAuthorizationService {

    private final StudentRepository studentRepository;

    /** true se o usuário autenticado tem role operacional/administrativa (não é um ALUNO comum). */
    public boolean hasElevatedPrivileges() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) return false;
        return auth.getAuthorities().stream()
            .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN")
                        || a.getAuthority().equals("ROLE_ROOT_MASTER")
                        || a.getAuthority().equals("ROLE_FINANCEIRO")
                        || a.getAuthority().equals("ROLE_SECRETARIA"));
    }

    /** true se o usuário autenticado é o próprio aluno (graduação) dono do registro {@code studentId}. */
    public boolean ownsStudentRecord(Long studentId) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (!(auth != null && auth.getPrincipal() instanceof UserDetailsImpl)) {
            return false;
        }
        UserDetailsImpl userDetails = (UserDetailsImpl) auth.getPrincipal();
        return studentRepository.findByUserId(userDetails.getId())
            .map(s -> s.getId().equals(studentId))
            .orElse(false);
    }

    /** true se o pagamento pertence ao aluno (graduação ou pós) autenticado. */
    public boolean ownsPayment(Payment payment) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (!(auth != null && auth.getPrincipal() instanceof UserDetailsImpl)) {
            return false;
        }
        Long userId = ((UserDetailsImpl) auth.getPrincipal()).getId();
        return payment.getStudent() != null
            && payment.getStudent().getUser() != null
            && payment.getStudent().getUser().getId().equals(userId);
    }
}
