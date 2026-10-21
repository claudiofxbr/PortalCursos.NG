package com.portalcursos.ng02.service;

import com.portalcursos.ng02.model.ChargePrice;
import com.portalcursos.ng02.model.StaffCourseScope;
import com.portalcursos.ng02.repository.StaffCourseScopeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Decide, no servidor, o que o usuário autenticado pode fazer na tabela de valores:
 * <ul>
 *   <li>ADMIN ({@code ROLE_ADMIN}/{@code ROLE_ROOT_MASTER}): vê e altera tudo e é o único que confirma;</li>
 *   <li>HEAD: {@code ROLE_SECRETARIA} com ao menos um curso em que é chefe; vê o seu escopo e propõe só nos
 *       cursos em que é chefe;</li>
 *   <li>SECRETARIA: {@code ROLE_SECRETARIA} com cursos vinculados, somente leitura;</li>
 *   <li>NONE: qualquer outro perfil (inclusive financeiro e aluno) não tem acesso.</li>
 * </ul>
 * Linhas globais (sem curso) são visíveis a quem tem algum escopo, mas só o administrador as altera.
 */
@Service
@RequiredArgsConstructor
public class ChargePriceAccessService {

    public enum Level { ADMIN, HEAD, SECRETARIA, NONE }

    public record Access(Level level, Set<UUID> courseIds, Set<UUID> headCourseIds, String actor) {

        public boolean isAdmin() {
            return level == Level.ADMIN;
        }

        public boolean hasAccess() {
            return level != Level.NONE;
        }

        public boolean canSee(ChargePrice p) {
            if (isAdmin()) return true;
            if (level == Level.NONE) return false;
            return p.getCourseId() == null || courseIds.contains(p.getCourseId());
        }

        public boolean canPropose(ChargePrice p) {
            return level == Level.HEAD && p.getCourseId() != null && headCourseIds.contains(p.getCourseId());
        }
    }

    private final StaffCourseScopeRepository scopeRepository;

    public Access current() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return new Access(Level.NONE, Set.of(), Set.of(), null);
        }
        String actor = auth.getName();
        boolean admin = hasAuthority(auth, "ROLE_ADMIN") || hasAuthority(auth, "ROLE_ROOT_MASTER");
        if (admin) {
            return new Access(Level.ADMIN, Set.of(), Set.of(), actor);
        }
        if (!hasAuthority(auth, "ROLE_SECRETARIA") || !(auth.getPrincipal() instanceof UserDetailsImpl user)
                || user.getId() == null) {
            return new Access(Level.NONE, Set.of(), Set.of(), actor);
        }
        List<StaffCourseScope> scopes = scopeRepository.findByStaffId(user.getId());
        if (scopes.isEmpty()) {
            return new Access(Level.NONE, Set.of(), Set.of(), actor);
        }
        Set<UUID> courses = new HashSet<>();
        Set<UUID> heads = new HashSet<>();
        for (StaffCourseScope s : scopes) {
            courses.add(s.getCourseId());
            if (s.isHead()) heads.add(s.getCourseId());
        }
        return new Access(heads.isEmpty() ? Level.SECRETARIA : Level.HEAD, Set.copyOf(courses), Set.copyOf(heads), actor);
    }

    private static boolean hasAuthority(Authentication auth, String authority) {
        return auth.getAuthorities().stream().anyMatch(a -> authority.equals(a.getAuthority()));
    }
}
