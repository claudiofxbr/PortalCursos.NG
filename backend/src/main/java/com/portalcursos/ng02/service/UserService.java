package com.portalcursos.ng02.service;

import com.portalcursos.ng02.model.Role;
import com.portalcursos.ng02.model.StaffMember;
import com.portalcursos.ng02.model.User;
import com.portalcursos.ng02.repository.RoleRepository;
import com.portalcursos.ng02.repository.StaffMemberRepository;
import com.portalcursos.ng02.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
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
public class UserService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private StaffMemberRepository staffMemberRepository;

    @Autowired
    private PasswordEncoder encoder;

    /**
     * Retorna o nível numérico da role para fins de hierarquia CRUD.
     */
    public int getRoleLevel(Role.ERole role) {
        if (role == null) return 0;
        switch (role) {
            case ROLE_ROOT_MASTER: return 100;
            case ROLE_ADMIN: return 80;
            case ROLE_COORDENADOR: return 60;
            case ROLE_SECRETARIA: return 40;
            case ROLE_FINANCEIRO:
            case ROLE_ACADEMICO:
            case ROLE_MATRICULA:
            case ROLE_PROFESSOR:
            case ROLE_MONITOR:
            case ROLE_BIBLIOTECARIO: return 20;
            default: return 0;
        }
    }

    /**
     * Retorna o nível mais alto entre as roles do usuário.
     */
    public int getMaxRoleLevel(User user) {
        if (user == null || user.getRoles() == null) return 0;
        return user.getRoles().stream()
                .map(r -> getRoleLevel(r.getName()))
                .max(Integer::compare)
                .orElse(0);
    }

    /**
     * Retorna o usuário atualmente autenticado no sistema.
     */
    public User getCurrentAuthenticatedUser() {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        String username;
        if (principal instanceof UserDetails) {
            username = ((UserDetails) principal).getUsername();
        } else {
            username = principal.toString();
        }
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("Erro: Usuário autenticado não encontrado no banco."));
    }

    /**
     * Lista usuários filtrados pela hierarquia do usuário logado.
     */
    public List<User> getAllUsers() {
        User currentUser = getCurrentAuthenticatedUser();
        int currentLevel = getMaxRoleLevel(currentUser);
        boolean isRoot = currentUser.getRoles().stream()
                .anyMatch(r -> r.getName() == Role.ERole.ROLE_ROOT_MASTER);

        List<User> allUsers = userRepository.findAll();

        if (isRoot) {
            return allUsers; // Root vê todos
        }

        // Outros veem apenas quem tem nível estritamente inferior
        return allUsers.stream()
                .filter(u -> getMaxRoleLevel(u) < currentLevel)
                .collect(Collectors.toList());
    }

    /**
     * Cria um novo usuário com as roles especificadas.
     * Se as roles não existirem no banco, elas são criadas (Seed).
     */
    @Transactional
    public User createUser(User user, Set<String> strRoles, String fullName, String position, String department) {
        User currentUser = getCurrentAuthenticatedUser();
        int currentLevel = getMaxRoleLevel(currentUser);
        boolean isRoot = currentUser.getRoles().stream()
                .anyMatch(r -> r.getName() == Role.ERole.ROLE_ROOT_MASTER);

        // Validar se o criador tem permissão para atribuir as roles solicitadas
        if (!isRoot) {
            for (String rStr : strRoles) {
                try {
                    String roleName = rStr.toUpperCase();
                    if (!roleName.startsWith("ROLE_")) roleName = "ROLE_" + roleName;
                    Role.ERole eRole = Role.ERole.valueOf(roleName);
                    if (getRoleLevel(eRole) >= currentLevel) {
                        throw new RuntimeException("Permissão insuficiente: Você não pode criar usuários com nível " + eRole + " (Nível igual ou superior ao seu).");
                    }
                } catch (IllegalArgumentException ignored) {}
            }
        }

        // Encriptar senha
        user.setPassword(encoder.encode(user.getPassword()));

        Set<Role> roles = new HashSet<>();

        if (strRoles == null || strRoles.isEmpty()) {
            Role userRole = getOrCreateRole(Role.ERole.ROLE_ALUNO);
            roles.add(userRole);
        } else {
            strRoles.forEach(role -> {
                try {
                    String roleName = role.toUpperCase();
                    if (!roleName.startsWith("ROLE_")) {
                        roleName = "ROLE_" + roleName;
                    }
                    roles.add(getOrCreateRole(Role.ERole.valueOf(roleName)));
                } catch (IllegalArgumentException e) {
                    roles.add(getOrCreateRole(Role.ERole.ROLE_ALUNO));
                }
            });
        }

        user.setRoles(roles);
        User savedUser = userRepository.saveAndFlush(user);

        // Lógica COLLAB V38.2: Sincronização Institucional Atômica e Robusta
        long startTime = System.currentTimeMillis();
        try {
            boolean isStaff = strRoles != null && strRoles.stream().anyMatch(r -> {
                String rUp = r.toUpperCase();
                return !rUp.contains("ALUNO") && !rUp.contains("CANDIDATO");
            });

            if (isStaff && fullName != null && !fullName.isBlank()) {
                if (savedUser.getId() == null) {
                    throw new RuntimeException("Falha crítica: O ID do usuário não foi gerado após o persist.");
                }

                System.out.println("[SUPREME-COLLAB-V38.3] Iniciando ativação institucional para ID: " + savedUser.getId());
                
                // Mapeia o StaffMember usando o mesmo ID do User (Protocolo MapsId)
                StaffMember staff = staffMemberRepository.findById(savedUser.getId())
                        .orElse(new StaffMember());
                
                staff.setUser(savedUser);
                // staff.setId(...) removido: O Hibernate gerencia via @MapsId
                staff.setFullName(fullName.trim());
                staff.setPosition(position != null && !position.isBlank() ? position.trim() : "COLABORADOR");
                staff.setDepartment(department != null && !department.isBlank() ? department.trim() : "INSTITUCIONAL");
                staff.setFotoUrl(savedUser.getFotoUrl());
                staff.setActive(true);
                
                // Força o flush para capturar erros de constraint imediatamente dentro deste try-catch
                staffMemberRepository.saveAndFlush(staff);
                System.out.println("[SUPREME-COLLAB-V38.2] Ativação concluída com sucesso em " + (System.currentTimeMillis() - startTime) + "ms");
            } else if (isStaff) {
                System.out.println("[SUPREME-COLLAB-V38.2] Ignorando ativação: Nome completo não fornecido ou inválido.");
            }
        } catch (Exception e) {
            System.err.println("[SUPREME-COLLAB-ERROR] Falha crítica na ativação para ID " + savedUser.getId() + ": " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("Falha ao sincronizar o perfil institucional. O registro foi cancelado para manter a integridade. Detalhe: " + e.getMessage());
        }

        return savedUser;
    }

    private Role getOrCreateRole(Role.ERole eRole) {
        return roleRepository.findByName(eRole)
                .orElseGet(() -> {
                    System.out.println("[SUPREME-SEED] Criando role inexistente: " + eRole);
                    return roleRepository.save(Role.builder().name(eRole).build());
                });
    }

    @Transactional
    public void deleteUser(Long id) {
        User targetUser = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Usuário não encontrado!"));
        
        User currentUser = getCurrentAuthenticatedUser();
        
        // Proteções Invioláveis
        if (targetUser.getUsername().equals("rootmaster")) {
            throw new RuntimeException("Atenção: O perfil Root Master original é protegido pelo protocolo de infraestrutura e não pode ser removido.");
        }
        
        if (targetUser.getId().equals(currentUser.getId())) {
            throw new RuntimeException("Você não pode remover seu próprio perfil através deste controle.");
        }

        int currentLevel = getMaxRoleLevel(currentUser);
        int targetLevel = getMaxRoleLevel(targetUser);
        boolean isRoot = currentUser.getRoles().stream()
                .anyMatch(r -> r.getName() == Role.ERole.ROLE_ROOT_MASTER);

        if (!isRoot && targetLevel >= currentLevel) {
            throw new RuntimeException("Permissão insuficiente: Você não pode remover um usuário de nível superior ou igual ao seu.");
        }

        userRepository.deleteById(id);
    }

    @Transactional
    public User updateUserRoles(Long id, Set<String> strRoles) {
        User targetUser = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Usuário não encontrado!"));

        User currentUser = getCurrentAuthenticatedUser();
        int currentLevel = getMaxRoleLevel(currentUser);
        int targetLevel = getMaxRoleLevel(targetUser);
        boolean isRoot = currentUser.getRoles().stream()
                .anyMatch(r -> r.getName() == Role.ERole.ROLE_ROOT_MASTER);

        // Validação de quem está sendo editado
        if (!isRoot && targetLevel >= currentLevel) {
            throw new RuntimeException("Permissão insuficiente: Você não pode alterar as permissões de um usuário de nível superior ou igual ao seu.");
        }

        // Validação das novas roles sendo atribuídas
        if (!isRoot) {
            for (String rStr : strRoles) {
                try {
                    String roleName = rStr.toUpperCase();
                    if (!roleName.startsWith("ROLE_")) roleName = "ROLE_" + roleName;
                    Role.ERole eRole = Role.ERole.valueOf(roleName);
                    if (getRoleLevel(eRole) >= currentLevel) {
                        throw new RuntimeException("Permissão insuficiente: Você não pode atribuir o nível " + eRole + " (Nível igual ou superior ao seu).");
                    }
                } catch (IllegalArgumentException ignored) {}
            }
        }

        Set<Role> roles = strRoles.stream()
                .map(r -> getOrCreateRole(Role.ERole.valueOf("ROLE_" + r.toUpperCase())))
                .collect(Collectors.toSet());

        targetUser.setRoles(roles);
        User savedUser = userRepository.saveAndFlush(targetUser);

        // Lógica COLLAB V38.2
        try {
            boolean isStaff = strRoles != null && strRoles.stream().anyMatch(r -> {
                String rUp = r.toUpperCase();
                return !rUp.contains("ALUNO") && !rUp.contains("CANDIDATO");
            });

            if (isStaff) {
                StaffMember staff = staffMemberRepository.findById(savedUser.getId())
                        .orElse(new StaffMember());
                
                staff.setUser(savedUser);
                if (staff.getFullName() == null || staff.getFullName().isBlank()) {
                    staff.setFullName(savedUser.getUsername().toUpperCase());
                }
                if (staff.getPosition() == null || staff.getPosition().isBlank()) {
                    staff.setPosition("CARGO_PENDENTE");
                }
                if (staff.getDepartment() == null || staff.getDepartment().isBlank()) {
                    staff.setDepartment("SETOR_PENDENTE");
                }
                staff.setFotoUrl(savedUser.getFotoUrl());
                staff.setActive(true);
                
                staffMemberRepository.saveAndFlush(staff);
            }
        } catch (Exception e) {
            System.err.println("[SUPREME-COLLAB-ERROR] Falha na atualização de roles para ID " + savedUser.getId() + ": " + e.getMessage());
        }

        return savedUser;
    }
}
