package com.portalcursos.ng02.config;

import com.portalcursos.ng02.model.Role;
import com.portalcursos.ng02.repository.RoleRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class DatabaseSeeder implements CommandLineRunner {

    @Autowired
    private RoleRepository roleRepository;

    @Override
    @Transactional
    public void run(String... args) throws Exception {
        if (roleRepository.count() == 0) {
            System.out.println("--- [DatabaseSeeder] Populando tabelas de permissoes (Roles) ---");
            
            for (Role.ERole erole : Role.ERole.values()) {
                if (!roleRepository.findByName(erole).isPresent()) {
                    roleRepository.save(Role.builder().name(erole).build());
                    System.out.println("[DatabaseSeeder] Criada Role: " + erole);
                }
            }
            
            System.out.println("--- [DatabaseSeeder] Todas as permissoes foram garantidas! ---");
        }
    }
}
