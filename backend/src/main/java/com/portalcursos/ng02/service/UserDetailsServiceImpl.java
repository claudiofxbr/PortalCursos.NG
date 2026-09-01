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
        logger.info("[OMEGA-AUTH] Buscando credenciais para: {}", maskIdentifier(usernameOrEmail));

        User user = userRepository.findByUsername(usernameOrEmail)
                .or(() -> userRepository.findByEmail(usernameOrEmail))
                .orElseThrow(() -> {
                    logger.warn("[OMEGA-AUTH] Usuário não encontrado: {}", maskIdentifier(usernameOrEmail));
                    return new UsernameNotFoundException("User Not Found with username or email: " + usernameOrEmail);
                });

        logger.info("[OMEGA-AUTH] Usuário autenticado: {}. Papéis ativos: {}", maskIdentifier(user.getUsername()), user.getRoles().size());

        return UserDetailsImpl.build(user);
    }

    /**
     * Minimização de dados em log (LGPD art. 6º III / GDPR art. 5(1)(c)): evita registrar
     * e-mail/username completos em texto plano nos logs de autenticação.
     */
    private static String maskIdentifier(String value) {
        if (value == null || value.isBlank()) return value;
        int at = value.indexOf('@');
        if (at > 0) {
            String local = value.substring(0, at);
            String domain = value.substring(at);
            String maskedLocal = local.length() <= 2 ? local.charAt(0) + "*" : local.substring(0, 2) + "***";
            return maskedLocal + domain;
        }
        return value.length() <= 2 ? value.charAt(0) + "*" : value.substring(0, 2) + "***";
    }

}
