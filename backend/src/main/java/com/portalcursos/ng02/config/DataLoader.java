package com.portalcursos.ng02.config;

import com.portalcursos.ng02.model.Role;
import com.portalcursos.ng02.model.*;
import com.portalcursos.ng02.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Set;

@Component
public class DataLoader implements CommandLineRunner {

    @Autowired
    RoleRepository roleRepository;

    @Autowired
    UserRepository userRepository;

    @Autowired
    StudentRepository studentRepository;

    @Autowired
    TeacherRepository teacherRepository;

    @Autowired
    PasswordEncoder encoder;

    @org.springframework.beans.factory.annotation.Value("${APP_ROOT_PASSWORD:admin123}")
    private String rootPass;

    @org.springframework.beans.factory.annotation.Value("${APP_ADMIN_PASSWORD:admin123}")
    private String adminPass;

    @Override
    public void run(String... args) throws Exception {
        System.out.println("[DataLoader] Iniciando inicialização de dados base no Neon...");

        // 1. Inicializar Roles se não existirem
        System.out.println("[DataLoader] Sincronizando perfis com o enum ERole...");
        for (Role.ERole erole : Role.ERole.values()) {
            roleRepository.findByName(erole).ifPresentOrElse(
                role -> System.out.println("[DataLoader] Role OK: " + erole),
                () -> {
                    roleRepository.save(Role.builder().name(erole).build());
                    System.out.println("[DataLoader] Criada Role: " + erole);
                }
            );
        }


        // 2. Criar ou Atualizar Usuário Root Master (TI/Desenvolvedor)
        userRepository.findByUsername("rootmaster").ifPresentOrElse(
            root -> {
                System.out.println("[DataLoader] [AUTH] Usuário ROOTMASTER localizado.");
                // Evitar redefinir senha hardcoded se já existir
                Role rootRole = roleRepository.findByName(Role.ERole.ROLE_ROOT_MASTER).orElseGet(() -> 
                    roleRepository.save(Role.builder().name(Role.ERole.ROLE_ROOT_MASTER).build()));
                if (!root.getRoles().contains(rootRole)) {
                    root.getRoles().add(rootRole);
                    userRepository.save(root);
                }
            },
            () -> {
                System.out.println("[DataLoader] [AUTH] Criando ROOTMASTER inicial...");
                Set<Role> roles = new HashSet<>();
                Role rootRole = roleRepository.findByName(Role.ERole.ROLE_ROOT_MASTER)
                        .orElseThrow(() -> new RuntimeException("Error: Role ROLE_ROOT_MASTER is not found."));
                roles.add(rootRole);
                User rootUser = User.builder()
                        .username("rootmaster")
                        .email("ti@portalcursos.com")
                        .password(encoder.encode(rootPass))
                        .roles(roles)
                        .build();
                userRepository.save(rootUser);
            }
        );

        // 3. Criar ou Atualizar Usuário Admin Inicial (Diretoria)
        userRepository.findByUsername("admin").ifPresentOrElse(
            admin -> {
                System.out.println("[DataLoader] [AUTH] Usuário ADMIN já existe. Garantindo permissões...");
                Role adminRole = roleRepository.findByName(Role.ERole.ROLE_ADMIN).orElseGet(() -> {
                    return roleRepository.save(Role.builder().name(Role.ERole.ROLE_ADMIN).build());
                });
                if (!admin.getRoles().contains(adminRole)) {
                    admin.getRoles().add(adminRole);
                    userRepository.save(admin);
                }
            },
            () -> {
                System.out.println("[DataLoader] [AUTH] Criando novo administrador inicial...");
                Set<Role> roles = new HashSet<>();
                Role adminRole = roleRepository.findByName(Role.ERole.ROLE_ADMIN)
                        .orElseThrow(() -> new RuntimeException("Error: Role ROLE_ADMIN is not found."));
                roles.add(adminRole);

                User adminUser = User.builder()
                        .username("admin")
                        .email("admin@portalcursos.com")
                        .password(encoder.encode(adminPass))
                        .roles(roles)
                        .build();

                userRepository.save(adminUser);
                System.out.println("[DataLoader] [AUTH] Administrador 'admin' CRIADO com sucesso!");
            }
        );

        System.out.println("[DataLoader] Inicialização concluída.");
    }
}
