package com.portalcursos.ng02.service;

import com.portalcursos.ng02.model.Role;
import com.portalcursos.ng02.model.Role.ERole;
import com.portalcursos.ng02.repository.RoleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Set;

/**
 * Ponto único de conversão de nomes de role (strings da API) para entidades {@link Role}.
 * Consolida o mapeamento que estava duplicado entre {@code AuthController.registerUser}
 * e {@code UserService.createUser/updateUser} (achado 1.4 da auditoria).
 *
 * <p>Role desconhecida lança {@link IllegalArgumentException} — o chamador decide como
 * traduzir isso para a API (ver {@code AuthController.registerUser} e
 * {@code UserService.createUser}, que convertem para {@code BusinessException}/400).
 * Até a auditoria de 2026-09 havia um modo "leniente" que engolia o erro e atribuía
 * {@code ROLE_ALUNO} silenciosamente — removido (achado 1.6): uma role mal digitada
 * na criação de usuário virava aluno sem aviso nenhum, em vez de falhar a operação.</p>
 */
@Component
@RequiredArgsConstructor
public class RoleResolver {

    private final RoleRepository roleRepository;

    /** Role desconhecida aborta a operação — nenhum chamador deve receber um resultado parcial/errado. */
    public Set<Role> resolveStrict(Set<String> strRoles) {
        Set<Role> roles = new HashSet<>();
        if (strRoles == null || strRoles.isEmpty()) {
            roles.add(getOrCreateRole(ERole.ROLE_ALUNO));
            return roles;
        }
        for (String raw : strRoles) {
            ERole target = mapToERole(raw);
            roles.add(getOrCreateRole(target));
        }
        return roles;
    }

    /**
     * Aceita tanto o nome curto ("financeiro") quanto o prefixado ("ROLE_FINANCEIRO"),
     * além dos apelidos históricos (staff/secretaria, teacher/professor, student/aluno,
     * rootmaster/root_master).
     */
    private ERole mapToERole(String raw) {
        if (raw == null) throw new IllegalArgumentException("Role nula");
        String key = raw.trim().toLowerCase();
        if (key.startsWith("role_")) key = key.substring(5);
        return switch (key) {
            case "admin" -> ERole.ROLE_ADMIN;
            case "root_master", "rootmaster" -> ERole.ROLE_ROOT_MASTER;
            case "staff", "secretaria" -> ERole.ROLE_SECRETARIA;
            case "financeiro" -> ERole.ROLE_FINANCEIRO;
            case "academico" -> ERole.ROLE_ACADEMICO;
            case "matricula" -> ERole.ROLE_MATRICULA;
            case "coordenador" -> ERole.ROLE_COORDENADOR;
            case "teacher", "professor" -> ERole.ROLE_PROFESSOR;
            case "monitor" -> ERole.ROLE_MONITOR;
            case "bibliotecario" -> ERole.ROLE_BIBLIOTECARIO;
            case "aluno", "student" -> ERole.ROLE_ALUNO;
            case "candidato" -> ERole.ROLE_CANDIDATO;
            default -> throw new IllegalArgumentException("Role desconhecida: " + raw);
        };
    }

    private Role getOrCreateRole(ERole eRole) {
        return roleRepository.findByName(eRole)
                .orElseGet(() -> roleRepository.save(Role.builder().name(eRole).build()));
    }
}
