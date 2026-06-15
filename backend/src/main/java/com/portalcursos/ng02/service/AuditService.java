package com.portalcursos.ng02.service;

import com.portalcursos.ng02.model.BaseAuditEntity;
import com.portalcursos.ng02.repository.StaffMemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

/**
 * Centraliza a injeção de metadados de auditoria (creator) nos entities.
 * Elimina duplicação do padrão injectAuditStamps presente em múltiplos controllers.
 */
@Service
@RequiredArgsConstructor
public class AuditService {

    private final StaffMemberRepository staffMemberRepository;

    public void injectCreator(BaseAuditEntity entity) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof UserDetailsImpl)) return;

        UserDetailsImpl userDetails = (UserDetailsImpl) auth.getPrincipal();
        Long userId = userDetails.getId();
        if (userId == null) return;
        staffMemberRepository.findById(userId)
                .ifPresent(entity::setCreator);
    }
}
