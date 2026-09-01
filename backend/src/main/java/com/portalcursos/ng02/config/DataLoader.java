package com.portalcursos.ng02.config;

import com.portalcursos.ng02.model.Role;
import com.portalcursos.ng02.model.User;
import com.portalcursos.ng02.repository.RoleRepository;
import com.portalcursos.ng02.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class DataLoader implements CommandLineRunner {

    private static final Logger logger = LoggerFactory.getLogger(DataLoader.class);

    private final RoleRepository roleRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder encoder;

    @Value("${APP_ROOT_PASSWORD:}")
    private String rootPass;

    @Value("${APP_ADMIN_PASSWORD:}")
    private String adminPass;

    private static final int MIN_PASSWORD_LENGTH = 12;

    @Override
    public void run(String... args) throws Exception {
        logger.info("[DataLoader] Iniciando inicialização de dados base no Neon...");

        if (rootPass == null || rootPass.isBlank() || rootPass.length() < MIN_PASSWORD_LENGTH) {
            throw new IllegalStateException(
                "[DataLoader] SEGURANÇA: APP_ROOT_PASSWORD não definida ou muito curta (mín. " + MIN_PASSWORD_LENGTH + " chars). " +
                "Defina a variável de ambiente APP_ROOT_PASSWORD antes de iniciar.");
        }
        if (adminPass == null || adminPass.isBlank() || adminPass.length() < MIN_PASSWORD_LENGTH) {
            throw new IllegalStateException(
                "[DataLoader] SEGURANÇA: APP_ADMIN_PASSWORD não definida ou muito curta (mín. " + MIN_PASSWORD_LENGTH + " chars). " +
                "Defina a variável de ambiente APP_ADMIN_PASSWORD antes de iniciar.");
        }

        // 1. Inicializar Roles se não existirem
        logger.info("[DataLoader] Sincronizando perfis com o enum ERole...");
        for (Role.ERole erole : Role.ERole.values()) {
            roleRepository.findByName(erole).ifPresentOrElse(
                role -> logger.debug("[DataLoader] Role OK: {}", erole),
                () -> {
                    roleRepository.save(Role.builder().name(erole).build());
                    logger.info("[DataLoader] Criada Role: {}", erole);
                }
            );
        }

        // 2. Criar ou Atualizar Usuário Root Master (TI/Desenvolvedor)
        userRepository.findByUsername("rootmaster").ifPresentOrElse(
            root -> {
                logger.info("[DataLoader] [AUTH] Usuário ROOTMASTER localizado.");
                Role rootRole = roleRepository.findByName(Role.ERole.ROLE_ROOT_MASTER).orElseGet(() ->
                    roleRepository.save(Role.builder().name(Role.ERole.ROLE_ROOT_MASTER).build()));
                if (!root.getRoles().contains(rootRole)) {
                    root.getRoles().add(rootRole);
                    userRepository.save(root);
                }
            },
            () -> {
                logger.info("[DataLoader] [AUTH] Criando ROOTMASTER inicial...");
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
                logger.info("[DataLoader] [AUTH] Usuário ADMIN já existe. Garantindo permissões...");
                Role adminRole = roleRepository.findByName(Role.ERole.ROLE_ADMIN).orElseGet(() ->
                    roleRepository.save(Role.builder().name(Role.ERole.ROLE_ADMIN).build()));
                if (!admin.getRoles().contains(adminRole)) {
                    admin.getRoles().add(adminRole);
                    userRepository.save(admin);
                }
            },
            () -> {
                logger.info("[DataLoader] [AUTH] Criando novo administrador inicial...");
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
                logger.info("[DataLoader] [AUTH] Administrador 'admin' CRIADO com sucesso!");
            }
        );

        logger.info("[DataLoader] Inicialização concluída.");
    }
}
