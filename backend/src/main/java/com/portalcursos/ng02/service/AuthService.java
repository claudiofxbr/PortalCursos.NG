package com.portalcursos.ng02.service;

import com.portalcursos.ng02.dto.JwtResponse;
import com.portalcursos.ng02.exception.AccountLockedException;
import com.portalcursos.ng02.model.User;
import com.portalcursos.ng02.model.UserSession;
import com.portalcursos.ng02.repository.UserRepository;
import com.portalcursos.ng02.repository.UserSessionRepository;
import com.portalcursos.ng02.security.JwtUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Lógica de negócio do login — extraída de {@code AuthController.authenticateUser}
 * (Lote E, item A1 da auditoria). O controller continua responsável por resolver o IP,
 * logar e traduzir o resultado/exceções em {@code ResponseEntity}; aqui fica autenticação,
 * criação de sessão e emissão de cookies.
 */
@Service
@RequiredArgsConstructor
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final UserRepository userRepository;
    private final JwtUtils jwtUtils;
    private final UserSessionRepository userSessionRepository;
    private final LoginAttemptService loginAttemptService;
    private final CookieService cookieService;

    /**
     * @throws AccountLockedException IP bloqueado por força bruta (HTTP 423 — ver AuthController).
     * @throws AuthenticationException usuário/senha inválidos (HTTP 401 — ver AuthController).
     */
    public JwtResponse login(String username, String password, String ipAddress,
                              HttpServletRequest request, HttpServletResponse response) {
        if (loginAttemptService.isBlocked(ipAddress)) {
            throw new AccountLockedException(
                    "Acesso temporariamente bloqueado por excesso de tentativas. Tente novamente em 15 minutos.");
        }

        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(username, password));
        } catch (AuthenticationException e) {
            loginAttemptService.loginFailed(ipAddress);
            throw e;
        }

        SecurityContextHolder.getContext().setAuthentication(authentication);

        UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal();
        User user = userRepository.findById(userDetails.getId())
                .orElseThrow(() -> new RuntimeException("Erro: Usuário não encontrado."));

        userSessionRepository.deleteByUser(user);
        loginAttemptService.loginSucceeded(ipAddress);

        String jwt = jwtUtils.generateTokenFromUserDetails(userDetails);
        String refreshTokenStr = UUID.randomUUID().toString();

        UserSession session = UserSession.builder()
                .user(user)
                .refreshToken(refreshTokenStr)
                .expiryDate(Instant.now().plusMillis(cookieService.getRefreshExpirationMs()))
                .userAgent(request.getHeader("User-Agent") != null ? request.getHeader("User-Agent") : "Unknown")
                .ipAddress(ipAddress)
                .build();
        userSessionRepository.save(session);

        // Setar cookies HttpOnly — tokens NÃO são expostos ao JavaScript
        response.addCookie(cookieService.buildAccessCookie(jwt));
        response.addCookie(cookieService.buildRefreshCookie(refreshTokenStr));

        List<String> roles = userDetails.getAuthorities().stream()
                .map(item -> item.getAuthority())
                .collect(Collectors.toList());

        // Retorna apenas dados de perfil — tokens viajam exclusivamente via cookie
        return new JwtResponse(null, null, userDetails.getId(), userDetails.getUsername(), userDetails.getEmail(), roles);
    }
}
