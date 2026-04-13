package com.portalcursos.ng02.service;

import com.portalcursos.ng02.model.Role;
import com.portalcursos.ng02.model.User;
import com.portalcursos.ng02.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.Set;

/**
 * PROTOCOLO SUPREMO V50.0 - CENTRAL DE GOVERNANÇA INSTITUCIONAL
 * Centraliza a inteligência de hierarquia e autoridade do sistema.
 */
@Service
public class AuthorityHierarchyService {

    @Autowired
    private UserRepository userRepository;

    /**
     * Níveis Institucionais:
     * ROOT_MASTER: 100
     * ADMIN: 80
     * COORDENADOR: 60
     * SECRETARIA: 40
     * OPERACIONAL: 20
     */
    public int getRoleLevel(Role.ERole role) {
        if (role == null) return 0;
        switch (role) {
            case ROLE_ROOT_MASTER: return 100;
            case ROLE_ADMIN: return 90;
            case ROLE_COORDENADOR: return 80;
            case ROLE_SECRETARIA:
            case ROLE_FINANCEIRO: return 70;
            case ROLE_PROFESSOR: return 60;
            case ROLE_ACADEMICO:
            case ROLE_MATRICULA: return 50;
            case ROLE_BIBLIOTECARIO:
            case ROLE_MONITOR: return 40;
            default: return 20; // Alunos, Candidatos e Visitantes
        }
    }

    public int getMaxLevel(User user) {
        if (user == null || user.getRoles() == null) return 0;
        return user.getRoles().stream()
                .map(r -> getRoleLevel(r.getName()))
                .max(Integer::compare)
                .orElse(0);
    }

    public int getMaxLevelFromRoles(Collection<String> strRoles) {
        if (strRoles == null) return 0;
        return strRoles.stream()
                .map(r -> {
                    try {
                        String name = r.toUpperCase();
                        if (!name.startsWith("ROLE_")) name = "ROLE_" + name;
                        return getRoleLevel(Role.ERole.valueOf(name));
                    } catch (Exception e) { return 10; }
                })
                .max(Integer::compare)
                .orElse(0);
    }

    public User getCurrentUser() {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        String username = (principal instanceof UserDetails) ? ((UserDetails) principal).getUsername() : principal.toString();
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("Erro de Infraestrutura: Usuário autenticado não encontrado."));
    }

    public boolean isRoot(User user) {
        return user.getRoles().stream().anyMatch(r -> r.getName() == Role.ERole.ROLE_ROOT_MASTER);
    }

    /**
     * Valida se o operador tem autoridade sobre o alvo.
     * Regra: Somente nível superior tem autoridade sobre nível inferior.
     */
    public void validateAuthority(User operator, User target) {
        if (isRoot(operator)) return; // Root manda em tudo
        
        if (target.getUsername().equals("rootmaster")) {
            throw new RuntimeException("Acesso Negado: O perfil Root Master é protegido e inalterável.");
        }

        int opLevel = getMaxLevel(operator);
        int tgLevel = getMaxLevel(target);

        if (tgLevel >= opLevel) {
            throw new RuntimeException("Permissão Negada: Você não possui autoridade institucional sobre este perfil (Nível " + tgLevel + " vs Seu Nível " + opLevel + ").");
        }
    }

    /**
     * Valida se o operador pode atribuir um set de roles.
     * Regra: O operador só pode atribuir roles de nível inferior ao seu.
     */
    public void validateRoleAssignment(User operator, Collection<String> strRoles) {
        if (isRoot(operator)) return;

        int opLevel = getMaxLevel(operator);
        int requestedLevel = getMaxLevelFromRoles(strRoles);

        if (requestedLevel >= opLevel) {
            throw new RuntimeException("Permissão Negada: Você não pode atribuir permissões de nível " + requestedLevel + " (Seu limite é " + (opLevel - 1) + ").");
        }
    }
}
