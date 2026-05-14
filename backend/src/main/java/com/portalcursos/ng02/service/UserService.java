package com.portalcursos.ng02.service;

import com.portalcursos.ng02.model.Role;
import com.portalcursos.ng02.model.StaffMember;
import com.portalcursos.ng02.model.User;
import com.portalcursos.ng02.repository.RoleRepository;
import com.portalcursos.ng02.repository.StaffMemberRepository;
import com.portalcursos.ng02.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final StaffMemberRepository staffMemberRepository;
    private final AuthorityHierarchyService authorityService;
    private final PasswordEncoder encoder;

    /**
     * Lista usuários filtrados pela hierarquia institucional.
     * Protocolo V50.0: Visibilidade estrita para níveis inferiores.
     */
    public List<User> getAllUsers() {
        User currentUser = authorityService.getCurrentUser();
        boolean isRoot = authorityService.isRoot(currentUser);
        int currentLevel = authorityService.getMaxLevel(currentUser);

        List<User> allUsers = userRepository.findAll();

        if (isRoot) return allUsers;

        return allUsers.stream()
                .filter(u -> authorityService.getMaxLevel(u) < currentLevel)
                .collect(Collectors.toList());
    }

    /**
     * Criação Atômica de Usuário e Colaborador (Protocolo SUPREMO V50.0)
     */
    @Transactional
    public User createUser(User user, Set<String> strRoles, String fullName, String position, String department) {
        User operator = authorityService.getCurrentUser();
        
        // 1. Validação de Autoridade para Atribuição de Roles
        authorityService.validateRoleAssignment(operator, strRoles);

        // 2. Preparação do Usuário
        user.setPassword(encoder.encode(user.getPassword()));
        
        Set<Role> roles = new HashSet<>();
        if (strRoles == null || strRoles.isEmpty()) {
            roles.add(getOrCreateRole(Role.ERole.ROLE_ALUNO));
        } else {
            strRoles.forEach(role -> {
                try {
                    String name = role.toUpperCase();
                    if (!name.startsWith("ROLE_")) name = "ROLE_" + name;
                    roles.add(getOrCreateRole(Role.ERole.valueOf(name)));
                } catch (Exception e) {
                    roles.add(getOrCreateRole(Role.ERole.ROLE_ALUNO));
                }
            });
        }
        user.setRoles(roles);

        // 3. Persistência do Usuário (Atomicidade Fase 1)
        User savedUser = userRepository.saveAndFlush(user);

        // 4. Sincronização Institucional (Atomicidade Fase 2)
        try {
            boolean isStaff = strRoles != null && strRoles.stream().anyMatch(r -> {
                String rUp = r.toUpperCase();
                return !rUp.contains("ALUNO") && !rUp.contains("CANDIDATO");
            });

            if (isStaff) {
                if (fullName == null || fullName.isBlank()) {
                    throw new RuntimeException("Dados Incompletos: Colaboradores exigem Nome Completo para ativação institucional.");
                }

                StaffMember staff = staffMemberRepository.findById(savedUser.getId())
                        .orElse(new StaffMember());
                
                staff.setUser(savedUser);
                staff.setFullName(fullName.trim());
                staff.setPosition(position != null && !position.isBlank() ? position.trim() : "COLABORADOR");
                staff.setDepartment(department != null && !department.isBlank() ? department.trim() : "INSTITUCIONAL");
                staff.setFotoUrl(savedUser.getFotoUrl());
                staff.setActive(true);
                
                staffMemberRepository.saveAndFlush(staff);
                System.out.println("[V50.0] Ativação Institucional concluída para: " + savedUser.getUsername());
            }
        } catch (Exception e) {
            System.err.println("[V50.0-ERROR] Falha na sincronização: " + e.getMessage());
            throw new RuntimeException("Falha Crítica de Governança: Não foi possível ativar o perfil institucional. Operação cancelada. " + e.getMessage());
        }

        return savedUser;
    }

    @Transactional
    public void deleteUser(Long id) {
        User operator = authorityService.getCurrentUser();
        User target = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Usuário não encontrado."));

        // Validação de Hierarquia Inviolável
        authorityService.validateAuthority(operator, target);

        if (target.getId().equals(operator.getId())) {
            throw new RuntimeException("Proteção de Perfil: Você não pode remover sua própria conta.");
        }

        userRepository.deleteById(id);
        System.out.println("[V50.0] Usuário deletado por autoridade superior: " + target.getUsername());
    }

    @Transactional
    public User updateUserRoles(Long id, Set<String> strRoles) {
        User operator = authorityService.getCurrentUser();
        User target = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Usuário não encontrado."));

        // 1. Validar autoridade sobre o alvo
        authorityService.validateAuthority(operator, target);

        // 2. Validar autoridade para as novas roles
        authorityService.validateRoleAssignment(operator, strRoles);

        Set<Role> roles = strRoles.stream()
                .map(r -> {
                    String name = r.toUpperCase();
                    if (!name.startsWith("ROLE_")) name = "ROLE_" + name;
                    return getOrCreateRole(Role.ERole.valueOf(name));
                })
                .collect(Collectors.toSet());

        target.setRoles(roles);
        User savedUser = userRepository.saveAndFlush(target);

        // 3. Atualização Institucional Condicional
        syncStaffMemberIfNecessary(savedUser, strRoles);

        return savedUser;
    }

    private void syncStaffMemberIfNecessary(User user, Set<String> strRoles) {
        try {
            boolean isStaff = strRoles.stream().anyMatch(r -> {
                String rUp = r.toUpperCase();
                return !rUp.contains("ALUNO") && !rUp.contains("CANDIDATO");
            });

            if (isStaff) {
                StaffMember staff = staffMemberRepository.findById(user.getId())
                        .orElse(new StaffMember());
                
                staff.setUser(user);
                if (staff.getFullName() == null) staff.setFullName(user.getUsername().toUpperCase());
                if (staff.getPosition() == null) staff.setPosition("CARGO_ATUALIZADO");
                if (staff.getDepartment() == null) staff.setDepartment("SETOR_ATUALIZADO");
                staff.setFotoUrl(user.getFotoUrl());
                staff.setActive(true);
                
                staffMemberRepository.saveAndFlush(staff);
            }
        } catch (Exception e) {
            System.err.println("[V50.0-WARN] Falha na sincronização parcial: " + e.getMessage());
        }
    }

    private Role getOrCreateRole(Role.ERole eRole) {
        return roleRepository.findByName(eRole)
                .orElseGet(() -> roleRepository.save(Role.builder().name(eRole).build()));
    }
}
