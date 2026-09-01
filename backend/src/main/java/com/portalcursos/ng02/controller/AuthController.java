package com.portalcursos.ng02.controller;

import com.portalcursos.ng02.dto.LoginRequest;
import com.portalcursos.ng02.dto.MessageResponse;
import com.portalcursos.ng02.dto.SignupRequest;
import com.portalcursos.ng02.model.Role;
import com.portalcursos.ng02.model.User;
import com.portalcursos.ng02.repository.RoleRepository;
import com.portalcursos.ng02.repository.UserRepository;
import com.portalcursos.ng02.security.JwtUtils;
import com.portalcursos.ng02.service.UserDetailsImpl;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.time.Instant;
import java.time.LocalDateTime;

import com.portalcursos.ng02.model.UserSession;
import com.portalcursos.ng02.repository.UserSessionRepository;
import lombok.RequiredArgsConstructor;
import com.portalcursos.ng02.repository.StaffMemberRepository;
import com.portalcursos.ng02.service.LoginAttemptService;
import com.portalcursos.ng02.model.StaffMember;


@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {
    private static final Logger logger = LoggerFactory.getLogger(AuthController.class);
    private static final String PRIVACY_POLICY_VERSION = "1.0";

    private final AuthenticationManager authenticationManager;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder encoder;
    private final JwtUtils jwtUtils;
    private final UserSessionRepository userSessionRepository;
    private final StaffMemberRepository staffMemberRepository;
    private final LoginAttemptService loginAttemptService;

    @Value("${portalcursos.jwt.refresh-expiration:86400000}")
    private long refreshExpirationMs;

    @Value("${portalcursos.jwt.expiration:900000}")
    private int jwtExpirationMs;

    @Value("${portalcursos.jwt.access-cookie-name:accessToken}")
    private String accessCookieName;

    @Value("${portalcursos.jwt.refresh-cookie-name:refreshToken}")
    private String refreshCookieName;

    @Value("${app.secure-cookies:true}")
    private boolean secureCookies;

    // ─── Helpers de Cookie ───────────────────────────────────────────────────

    /** Cria um cookie HttpOnly para o token de acesso. */
    private Cookie buildAccessCookie(String token) {
        Cookie cookie = new Cookie(accessCookieName, token);
        cookie.setHttpOnly(true);
        cookie.setSecure(secureCookies);
        cookie.setPath("/");
        cookie.setMaxAge(jwtExpirationMs / 1000);
        cookie.setAttribute("SameSite", "Strict");
        return cookie;
    }

    /** Cria um cookie HttpOnly para o refresh token. */
    private Cookie buildRefreshCookie(String token) {
        Cookie cookie = new Cookie(refreshCookieName, token);
        cookie.setHttpOnly(true);
        cookie.setSecure(secureCookies);
        cookie.setPath("/api/auth/refreshtoken");
        cookie.setMaxAge((int) (refreshExpirationMs / 1000));
        cookie.setAttribute("SameSite", "Strict");
        return cookie;
    }

    /** Seta cookies de expiração para realizar o logout. */
    private void clearAuthCookies(HttpServletResponse response) {
        Cookie access = new Cookie(accessCookieName, "");
        access.setHttpOnly(true);
        access.setSecure(secureCookies);
        access.setPath("/");
        access.setMaxAge(0);
        access.setAttribute("SameSite", "Strict");

        Cookie refresh = new Cookie(refreshCookieName, "");
        refresh.setHttpOnly(true);
        refresh.setSecure(secureCookies);
        refresh.setPath("/api/auth/refreshtoken");
        refresh.setMaxAge(0);
        refresh.setAttribute("SameSite", "Strict");

        response.addCookie(access);
        response.addCookie(refresh);
    }

    /**
     * Extrai o IP real do cliente atrás do nginx (devops/scripts/nginx.conf).
     * X-Real-IP é preferido: nginx o define via proxy_set_header (sobrescreve, não
     * concatena), então não é falsificável pelo cliente. X-Forwarded-For usa
     * $proxy_add_x_forwarded_for, que ANEXA ao valor recebido — um cliente malicioso
     * pode enviar "X-Forwarded-For: 1.2.3.4" e nginx só adiciona o IP real depois
     * dele; usar o primeiro valor (como antes) permitia falsificar o IP e burlar o
     * bloqueio de força bruta. Por isso, se X-Real-IP faltar, usamos o ÚLTIMO valor
     * da cadeia X-Forwarded-For (o único segmento que o nginx realmente controla).
     */
    private String extractClientIp(HttpServletRequest request) {
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isEmpty()) {
            return realIp;
        }
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isEmpty()) {
            String[] parts = forwardedFor.split(",");
            return parts[parts.length - 1].trim();
        }
        return request.getRemoteAddr();
    }

    /** Extrai o refresh token do cookie ou, como fallback, do body da requisição. */
    private String extractRefreshToken(HttpServletRequest request, String bodyToken) {
        if (request.getCookies() != null) {
            return Arrays.stream(request.getCookies())
                    .filter(c -> refreshCookieName.equals(c.getName()))
                    .map(Cookie::getValue)
                    .findFirst()
                    .orElse(bodyToken);
        }
        return bodyToken;
    }

    // ─── Endpoints ───────────────────────────────────────────────────────────

    @PostMapping("/signin")
    public ResponseEntity<?> authenticateUser(
            @Valid @RequestBody LoginRequest loginRequest,
            HttpServletRequest request,
            HttpServletResponse response) {

        String ipAddress = extractClientIp(request);

        logger.info("[AUTH API] [SIGNIN] Tentativa de login: {} de {}", loginRequest.getUsername(), ipAddress);

        if (loginAttemptService.isBlocked(ipAddress)) {
            logger.warn("[SECURITY] Tentativa de login bloqueada para IP: {}", ipAddress);
            return ResponseEntity
                    .status(org.springframework.http.HttpStatus.LOCKED)
                    .body(new MessageResponse("Acesso temporariamente bloqueado por excesso de tentativas. Tente novamente em 15 minutos."));
        }

        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(loginRequest.getUsername(), loginRequest.getPassword()));

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
                    .expiryDate(Instant.now().plusMillis(refreshExpirationMs))
                    .userAgent(request.getHeader("User-Agent") != null ? request.getHeader("User-Agent") : "Unknown")
                    .ipAddress(ipAddress)
                    .build();
            userSessionRepository.save(session);

            // Setar cookies HttpOnly — tokens NÃO são mais expostos ao JavaScript
            response.addCookie(buildAccessCookie(jwt));
            response.addCookie(buildRefreshCookie(refreshTokenStr));

            List<String> roles = userDetails.getAuthorities().stream()
                    .map(item -> item.getAuthority())
                    .collect(Collectors.toList());

            logger.info("[AUTH API] [SUCCESS] Usuário {} autenticado com sucesso.", loginRequest.getUsername());

            // Retorna apenas dados de perfil — tokens viajam exclusivamente via cookie
            return ResponseEntity.ok(new com.portalcursos.ng02.dto.JwtResponse(
                    null, null,
                    userDetails.getId(), userDetails.getUsername(), userDetails.getEmail(), roles));

        } catch (org.springframework.security.core.AuthenticationException e) {
            loginAttemptService.loginFailed(ipAddress);
            logger.error("[AUTH API] [FAILURE] Falha na autenticação para {}: {}", loginRequest.getUsername(), e.getMessage());
            return ResponseEntity
                    .status(org.springframework.http.HttpStatus.UNAUTHORIZED)
                    .body(new MessageResponse("Erro de Autenticação: Usuário ou senha inválidos."));
        } catch (Exception e) {
            logger.error("[AUTH API] [ERROR] Erro inesperado no login para {}: {}", loginRequest.getUsername(), e.getMessage());
            return ResponseEntity
                    .status(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new MessageResponse("Erro interno no servidor de autenticação."));
        }
    }

    @PostMapping("/refreshtoken")
    @org.springframework.transaction.annotation.Transactional
    public ResponseEntity<?> refreshtoken(
            HttpServletRequest request,
            HttpServletResponse response,
            @RequestBody(required = false) com.portalcursos.ng02.dto.TokenRefreshRequest body) {

        String ipAddress = extractClientIp(request);
        String rateLimitKey = "refresh:" + ipAddress;

        if (loginAttemptService.isBlocked(rateLimitKey)) {
            logger.warn("[SECURITY] Tentativa de refresh token bloqueada para IP: {}", ipAddress);
            return ResponseEntity
                    .status(org.springframework.http.HttpStatus.LOCKED)
                    .body(new MessageResponse("Acesso temporariamente bloqueado por excesso de tentativas. Tente novamente em 15 minutos."));
        }

        // Cookie tem prioridade; body é fallback para compatibilidade
        String bodyToken = (body != null) ? body.getRefreshToken() : null;
        String refreshToken = extractRefreshToken(request, bodyToken);

        if (refreshToken == null || refreshToken.isBlank()) {
            return ResponseEntity.badRequest().body(new MessageResponse("Refresh token não fornecido."));
        }

        logger.info("[AUTH] Renovação de token: ...{}", refreshToken.length() > 5 ? refreshToken.substring(refreshToken.length() - 5) : "?");

        try {
            return userSessionRepository.findByRefreshToken(refreshToken)
                    .map(session -> {
                        if (session.getExpiryDate().isBefore(Instant.now())) {
                            userSessionRepository.delete(session);
                            clearAuthCookies(response);
                            loginAttemptService.loginFailed(rateLimitKey);
                            logger.warn("[AUTH] Refresh token expirado.");
                            return ResponseEntity.status(403).body(new MessageResponse("Sessão expirada. Faça login novamente."));
                        }

                        User user = session.getUser();
                        String newAccessToken = jwtUtils.generateTokenFromUser(user);

                        // Rotação: novo refresh token, invalida o anterior
                        String newRefreshToken = UUID.randomUUID().toString();
                        session.setRefreshToken(newRefreshToken);
                        session.setExpiryDate(Instant.now().plusMillis(refreshExpirationMs));
                        userSessionRepository.save(session);

                        response.addCookie(buildAccessCookie(newAccessToken));
                        response.addCookie(buildRefreshCookie(newRefreshToken));

                        loginAttemptService.loginSucceeded(rateLimitKey);
                        logger.info("[AUTH] Token renovado para: {}", user.getUsername());
                        // Retorna apenas confirmação — tokens viajam via cookie
                        return ResponseEntity.ok(new MessageResponse("Token renovado com sucesso."));
                    })
                    .orElseGet(() -> {
                        clearAuthCookies(response);
                        loginAttemptService.loginFailed(rateLimitKey);
                        logger.warn("[AUTH] Refresh token não encontrado.");
                        return ResponseEntity.status(403).body(new MessageResponse("Sessão inválida. Faça login novamente."));
                    });
        } catch (Exception e) {
            logger.error("[AUTH] Erro crítico no refresh token: ", e);
            return ResponseEntity.status(500).body(new MessageResponse("Erro interno ao renovar sessão."));
        }
    }

    @GetMapping("/me")
    public ResponseEntity<?> getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !authentication.isAuthenticated()
                || "anonymousUser".equals(authentication.getPrincipal())) {
            return ResponseEntity.status(401).body(new MessageResponse("Não autenticado."));
        }

        UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal();

        String position = "";
        String fotoUrl = "";
        Optional<StaffMember> staff = staffMemberRepository.findById(userDetails.getId());
        if (staff.isPresent()) {
            position = staff.get().getPosition();
            fotoUrl = staff.get().getFotoUrl();
        }

        return ResponseEntity.ok(new com.portalcursos.ng02.dto.UserInfoResponse(
                userDetails.getId(),
                userDetails.getUsername(),
                userDetails.getEmail(),
                userDetails.getAuthorities().stream()
                        .map(item -> item.getAuthority())
                        .collect(Collectors.toList()),
                position,
                fotoUrl));
    }

    @PostMapping("/signout")
    public ResponseEntity<?> logoutUser(
            HttpServletRequest request,
            HttpServletResponse response,
            @RequestBody(required = false) com.portalcursos.ng02.dto.TokenRefreshRequest body) {

        String bodyToken = (body != null) ? body.getRefreshToken() : null;
        String refreshToken = extractRefreshToken(request, bodyToken);

        logger.info("[AUTH] Encerrando sessão.");

        if (refreshToken != null && !refreshToken.isBlank()) {
            userSessionRepository.findByRefreshToken(refreshToken)
                    .ifPresent(session -> {
                        userSessionRepository.delete(session);
                        logger.info("[AUTH] Sessão removida do banco.");
                    });
        }

        clearAuthCookies(response);
        return ResponseEntity.ok(new MessageResponse("Logout realizado com sucesso."));
    }

    @PostMapping("/signup")
    public ResponseEntity<?> registerUser(
            @Valid @RequestBody SignupRequest signUpRequest,
            HttpServletRequest request) {
        logger.info("[AUTH] [SIGNUP] Tentativa de registro: {}", signUpRequest.getUsername());

        String ipAddress = extractClientIp(request);
        String rateLimitKey = "signup:" + ipAddress;

        if (loginAttemptService.isBlocked(rateLimitKey)) {
            logger.warn("[SECURITY] Tentativa de registro bloqueada para IP: {}", ipAddress);
            return ResponseEntity
                    .status(org.springframework.http.HttpStatus.LOCKED)
                    .body(new MessageResponse("Muitas tentativas de registro. Tente novamente em 15 minutos."));
        }
        // Conta a tentativa independentemente do resultado — o próprio volume de
        // signups (mesmo bem-sucedidos) é o vetor de abuso que queremos limitar.
        loginAttemptService.loginFailed(rateLimitKey);

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
                logger.warn("[SECURITY] Tentativa de registro com roles privilegiadas bloqueada: {}", signUpRequest.getUsername());
                return ResponseEntity.status(403)
                        .body(new MessageResponse("Apenas administradores podem registrar contas privilegiadas."));
            }
        }

        if (userRepository.existsByUsername(signUpRequest.getUsername())) {
            return ResponseEntity.badRequest()
                    .body(new MessageResponse("Erro: Nome de usuário já está em uso."));
        }

        if (userRepository.existsByEmail(signUpRequest.getEmail())) {
            return ResponseEntity.badRequest()
                    .body(new MessageResponse("Erro: E-mail já está em uso."));
        }

        try {
            User user = User.builder()
                    .username(signUpRequest.getUsername())
                    .email(signUpRequest.getEmail())
                    .password(encoder.encode(signUpRequest.getPassword()))
                    .privacyConsentAccepted(true)
                    .privacyConsentVersion(PRIVACY_POLICY_VERSION)
                    .privacyConsentAt(LocalDateTime.now())
                    .build();

            Set<String> strRoles = signUpRequest.getRole();
            Set<Role> roles = new HashSet<>();

            if (strRoles == null || strRoles.isEmpty()) {
                roles.add(roleRepository.findByName(Role.ERole.ROLE_ALUNO)
                        .orElseThrow(() -> new RuntimeException("ROLE_ALUNO não encontrado.")));
            } else {
                for (String role : strRoles) {
                    Role.ERole targetRole = switch (role.toLowerCase()) {
                        case "admin" -> Role.ERole.ROLE_ADMIN;
                        case "root_master", "rootmaster" -> Role.ERole.ROLE_ROOT_MASTER;
                        case "staff", "secretaria" -> Role.ERole.ROLE_SECRETARIA;
                        case "financeiro" -> Role.ERole.ROLE_FINANCEIRO;
                        case "academico" -> Role.ERole.ROLE_ACADEMICO;
                        case "matricula" -> Role.ERole.ROLE_MATRICULA;
                        case "coordenador" -> Role.ERole.ROLE_COORDENADOR;
                        case "teacher", "professor" -> Role.ERole.ROLE_PROFESSOR;
                        case "monitor" -> Role.ERole.ROLE_MONITOR;
                        case "bibliotecario" -> Role.ERole.ROLE_BIBLIOTECARIO;
                        case "aluno", "student" -> Role.ERole.ROLE_ALUNO;
                        case "candidato" -> Role.ERole.ROLE_CANDIDATO;
                        default -> throw new IllegalArgumentException("Role desconhecida: " + role);
                    };
                    roles.add(roleRepository.findByName(targetRole)
                            .orElseThrow(() -> new RuntimeException(targetRole + " não encontrado.")));
                }
            }

            user.setRoles(roles);
            userRepository.save(user);

            logger.info("[AUTH] [SIGNUP-SUCCESS] Usuário {} registrado.", signUpRequest.getUsername());
            return ResponseEntity.ok(new MessageResponse("Usuário registrado com sucesso."));

        } catch (IllegalArgumentException e) {
            // Mensagem de IllegalArgumentException aqui é controlada (ex.: "Role desconhecida: X"),
            // mas delega ao GlobalExceptionHandler via BusinessException para manter formato de erro consistente.
            throw new com.portalcursos.ng02.exception.BusinessException(e.getMessage());
        } catch (Exception e) {
            logger.error("[AUTH] [SIGNUP-ERROR] Falha ao salvar usuário: ", e);
            return ResponseEntity
                    .status(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new MessageResponse("Erro interno ao criar usuário. Tente novamente."));
        }
    }
}
