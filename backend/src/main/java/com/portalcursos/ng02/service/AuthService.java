package com.portalcursos.ng02.service;

import com.portalcursos.ng02.dto.JwtResponse;
import com.portalcursos.ng02.exception.AccountLockedException;
import com.portalcursos.ng02.exception.InvalidSessionException;
import com.portalcursos.ng02.exception.SessionExpiredException;
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

    /**
     * Renova a sessão a partir do refresh token (rotação: emite um novo, invalida o anterior).
     * O bloqueio por força bruta (isBlocked) e a validação de presença do token continuam no
     * controller — são checagens de entrada da requisição, não lógica de domínio da sessão.
     *
     * @return o username do dono da sessão renovada (controller usa para o log mascarado).
     * @throws InvalidSessionException refresh token não encontrado (HTTP 403 — ver AuthController).
     * @throws SessionExpiredException refresh token encontrado mas expirado (HTTP 403 — ver AuthController).
     */
    public String refreshToken(String refreshToken, String rateLimitKey, HttpServletResponse response) {
        UserSession session = userSessionRepository.findByRefreshToken(refreshToken)
                .orElseThrow(() -> {
                    cookieService.clearAuthCookies(response);
                    loginAttemptService.loginFailed(rateLimitKey);
                    return new InvalidSessionException("Sessão inválida. Faça login novamente.");
                });

        if (session.getExpiryDate().isBefore(Instant.now())) {
            userSessionRepository.delete(session);
            cookieService.clearAuthCookies(response);
            loginAttemptService.loginFailed(rateLimitKey);
            throw new SessionExpiredException("Sessão expirada. Faça login novamente.");
        }

        User user = session.getUser();
        String newAccessToken = jwtUtils.generateTokenFromUser(user);

        // Rotação: novo refresh token, invalida o anterior
        String newRefreshToken = UUID.randomUUID().toString();
        session.setRefreshToken(newRefreshToken);
        session.setExpiryDate(Instant.now().plusMillis(cookieService.getRefreshExpirationMs()));
        userSessionRepository.save(session);

        response.addCookie(cookieService.buildAccessCookie(newAccessToken));
        response.addCookie(cookieService.buildRefreshCookie(newRefreshToken));

        loginAttemptService.loginSucceeded(rateLimitKey);
        return user.getUsername();
    }
}
