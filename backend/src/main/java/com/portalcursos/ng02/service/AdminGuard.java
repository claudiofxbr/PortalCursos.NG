package com.portalcursos.ng02.service;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Autorização explícita do administrador para ações que equivalem a perdoar ou apagar uma cobrança (excluir a
 * fatura, reduzir ou zerar o valor). É checada no serviço, além do {@code @PreAuthorize} da rota, para que nenhum
 * outro caminho de código consiga contornar a regra.
 */
@Component
public class AdminGuard {

    /** O usuário autenticado é administrador (ROLE_ADMIN ou ROLE_ROOT_MASTER). */
    public boolean isAdmin() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.isAuthenticated() && auth.getAuthorities().stream()
                .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()) || "ROLE_ROOT_MASTER".equals(a.getAuthority()));
    }

    /** Nome do usuário autenticado, para o registro de auditoria. */
    public String actor() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth == null ? "desconhecido" : auth.getName();
    }

    /** Exige administrador; senão lança 403 dizendo qual ação foi negada. */
    public void require(String action) {
        if (!isAdmin()) {
            throw new AccessDeniedException("Somente um administrador pode " + action);
        }
    }
}
