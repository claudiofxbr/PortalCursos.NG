package com.portalcursos.ng02.service;

import com.portalcursos.ng02.model.Role;
import com.portalcursos.ng02.model.User;
import com.portalcursos.ng02.repository.RoleRepository;
import com.portalcursos.ng02.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class UserService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private PasswordEncoder encoder;

    /**
     * Lista todos os usuários cadastrados.
     */
    public List<User> getAllUsers() {
        return userRepository.findAll();
    }

    /**
     * Cria um novo usuário com as roles especificadas.
     * Se as roles não existirem no banco, elas são criadas (Seed).
     */
    @Transactional
    public User createUser(User user, Set<String> strRoles) {
        // Encriptar senha
        user.setPassword(encoder.encode(user.getPassword()));

        Set<Role> roles = new HashSet<>();

        if (strRoles == null || strRoles.isEmpty()) {
            Role userRole = getOrCreateRole(Role.ERole.ROLE_ALUNO);
            roles.add(userRole);
        } else {
            strRoles.forEach(role -> {
                try {
                    // Tenta mapear diretamente a string para o Enum (ex: 'root_master' -> ROLE_ROOT_MASTER)
                    String roleName = role.toUpperCase();
                    if (!roleName.startsWith("ROLE_")) {
                        roleName = "ROLE_" + roleName;
                    }
                    roles.add(getOrCreateRole(Role.ERole.valueOf(roleName)));
                } catch (IllegalArgumentException e) {
                    // Fallback para ALUNO se a role for desconhecida
                    roles.add(getOrCreateRole(Role.ERole.ROLE_ALUNO));
                }
            });
        }

        user.setRoles(roles);
        return userRepository.save(user);
    }

    private Role getOrCreateRole(Role.ERole eRole) {
        return roleRepository.findByName(eRole)
                .orElseGet(() -> roleRepository.save(Role.builder().name(eRole).build()));
    }

    @Transactional
    public void deleteUser(Long id) {
        userRepository.deleteById(id);
    }

    @Transactional
    public User updateUserRoles(Long id, Set<String> strRoles) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Usuário não encontrado!"));

        Set<Role> roles = strRoles.stream()
                .map(r -> getOrCreateRole(Role.ERole.valueOf("ROLE_" + r.toUpperCase())))
                .collect(Collectors.toSet());

        user.setRoles(roles);
        return userRepository.save(user);
    }
}
