package com.portalcursos.ng02.config;

import com.portalcursos.ng02.model.Role;
import com.portalcursos.ng02.repository.RoleRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

// @Component
public class DatabaseSeeder implements CommandLineRunner {

    @Autowired
    private RoleRepository roleRepository;

    @Override
    @Transactional
    public void run(String... args) throws Exception {
        // Desativado para evitar concorrencia com o DataLoader.java
        // O seeding de Roles e contas iniciais e tratado exclusivamente pelo DataLoader.
    }
}
