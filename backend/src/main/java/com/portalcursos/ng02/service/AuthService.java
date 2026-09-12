package com.portalcursos.ng02.service;

import com.portalcursos.ng02.dto.JwtResponse;
import com.portalcursos.ng02.dto.SignupRequest;
import com.portalcursos.ng02.exception.AccountLockedException;
import com.portalcursos.ng02.exception.BusinessException;
import com.portalcursos.ng02.exception.InvalidSessionException;
import com.portalcursos.ng02.exception.SessionExpiredException;
import com.portalcursos.ng02.exception.SignupConflictException;
import com.portalcursos.ng02.exception.SignupPrivilegeException;
import com.portalcursos.ng02.model.Role;
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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Lógica de negócio de autenticação — extraída do {@code AuthController} (Lote E da
 * auditoria, itens A1/A2/A3). O controller continua responsável pelas checagens de entrada
 * da requisição (IP, bloqueio por força bruta, presença de token), logging e tradução do
 * resultado/exceções em {@code ResponseEntity}; aqui fica login, renovação de sessão e
 * cadastro de usuário.
 */
@Service
@RequiredArgsConstructor
public class AuthService {

    private static final String PRIVACY_POLICY_VERSION = "1.0";

    private final AuthenticationManager authenticationManager;
    private final UserRepository userRepository;
    private final JwtUtils jwtUtils;
    private final UserSessionRepository userSessionRepository;
    private final LoginAttemptService loginAttemptService;
    private final CookieService cookieService;
    private final PasswordEncoder encoder;
    private final RoleResolver roleResolver;

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

    /**
     * Cadastro de usuário. O bloqueio por força bruta (isBlocked) e a contagem da
     * tentativa continuam no controller — mesmo critério dos outros itens do Lote E.
     *
     * @throws SignupPrivilegeException role privilegiada pedida sem autenticação elevada (HTTP 403 — ver AuthController).
     * @throws SignupConflictException  username ou e-mail já em uso (HTTP 400 — ver AuthController).
     * @throws BusinessException        role desconhecida (HTTP 400, formato padrão do GlobalExceptionHandler).
     */
    public void signup(SignupRequest signUpRequest) {
        Set<String> requestedRoles = signUpRequest.getRole();
        // Qualquer role que não seja ALUNO ou CANDIDATO exige autenticação com privilégios elevados
        boolean isRequestingPrivilegedRoles = requestedRoles != null && requestedRoles.stream()
                .anyMatch(role -> {
                    String r = role.toUpperCase();
                    return !r.equals("ALUNO") && !r.equals("CANDIDATO")
                            && !r.equals("STUDENT") && !r.equals("ROLE_STUDENT")
                            && !r.equals("ROLE_ALUNO") && !r.equals("ROLE_CANDIDATO");
                });

        if (isRequestingPrivilegedRoles) {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            boolean hasElevatedPrivileges = auth != null && auth.isAuthenticated()
                    && !"anonymousUser".equals(auth.getPrincipal())
                    && auth.getAuthorities().stream()
                            .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN")
                                    || a.getAuthority().equals("ROLE_ROOT_MASTER"));

            if (!hasElevatedPrivileges) {
                throw new SignupPrivilegeException("Apenas administradores podem registrar contas privilegiadas.");
            }
        }

        if (userRepository.existsByUsername(signUpRequest.getUsername())) {
            throw new SignupConflictException("Erro: Nome de usuário já está em uso.");
        }

        if (userRepository.existsByEmail(signUpRequest.getEmail())) {
            throw new SignupConflictException("Erro: E-mail já está em uso.");
        }

        User user = User.builder()
                .username(signUpRequest.getUsername())
                .email(signUpRequest.getEmail())
                .password(encoder.encode(signUpRequest.getPassword()))
                .privacyConsentAccepted(true)
                .privacyConsentVersion(PRIVACY_POLICY_VERSION)
                .privacyConsentAt(LocalDateTime.now())
                .build();

        try {
            Set<Role> roles = roleResolver.resolveStrict(signUpRequest.getRole());
            user.setRoles(roles);
        } catch (IllegalArgumentException e) {
            // Mensagem controlada (ex.: "Role desconhecida: X") — delega ao GlobalExceptionHandler
            // via BusinessException para manter o formato de erro padrão.
            throw new BusinessException(e.getMessage());
        }

        userRepository.save(user);
    }
}
