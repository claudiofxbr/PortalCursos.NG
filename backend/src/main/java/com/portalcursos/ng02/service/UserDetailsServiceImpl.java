package com.portalcursos.ng02.service;

import com.portalcursos.ng02.model.User;
import com.portalcursos.ng02.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserDetailsServiceImpl implements UserDetailsService {

    @Autowired
    UserRepository userRepository;

    @Override
    @Transactional
    public UserDetails loadUserByUsername(String usernameOrEmail) throws UsernameNotFoundException {
        System.out.println("[AUTH DEBUG] Buscando usuario: " + usernameOrEmail);
        User user = userRepository.findByUsername(usernameOrEmail)
                .or(() -> userRepository.findByEmail(usernameOrEmail))
                .orElseThrow(() -> new UsernameNotFoundException("User Not Found with username or email: " + usernameOrEmail));

        System.out.println("[AUTH DEBUG] Usuario encontrado: " + user.getUsername());
        System.out.println("[AUTH DEBUG] Roles carregadas: " + user.getRoles().size());
        user.getRoles().forEach(r -> System.out.println("   -> Role: " + r.getName()));

        return UserDetailsImpl.build(user);
    }

}
