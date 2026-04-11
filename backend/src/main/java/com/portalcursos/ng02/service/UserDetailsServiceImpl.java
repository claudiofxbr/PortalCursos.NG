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

    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(UserDetailsServiceImpl.class);

    @Autowired
    UserRepository userRepository;

    @Override
    @Transactional
    public UserDetails loadUserByUsername(String usernameOrEmail) throws UsernameNotFoundException {
        logger.info("[OMEGA-AUTH] Buscando credenciais para: {}", usernameOrEmail);
        
        User user = userRepository.findByUsername(usernameOrEmail)
                .or(() -> userRepository.findByEmail(usernameOrEmail))
                .orElseThrow(() -> {
                    logger.warn("[OMEGA-AUTH] Usuário não encontrado: {}", usernameOrEmail);
                    return new UsernameNotFoundException("User Not Found with username or email: " + usernameOrEmail);
                });

        logger.info("[OMEGA-AUTH] Usuário autenticado: {}. Papéis ativos: {}", user.getUsername(), user.getRoles().size());
        
        return UserDetailsImpl.build(user);
    }

}
