package com.portalcursos.ng02.controller;

import com.portalcursos.ng02.dto.LoginRequest;
import com.portalcursos.ng02.dto.MessageResponse;
import com.portalcursos.ng02.dto.SignupRequest;
import com.portalcursos.ng02.service.UserDetailsImpl;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.Optional;
import java.util.stream.Collectors;

import com.portalcursos.ng02.repository.UserSessionRepository;
import lombok.RequiredArgsConstructor;
import com.portalcursos.ng02.repository.StaffMemberRepository;
import com.portalcursos.ng02.service.AuthService;
import com.portalcursos.ng02.service.LoginAttemptService;
import com.portalcursos.ng02.service.CookieService;
import com.portalcursos.ng02.model.StaffMember;
import com.portalcursos.ng02.exception.AccountLockedException;


@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {
    private static final Logger logger = LoggerFactory.getLogger(AuthController.class);

    private final UserSessionRepository userSessionRepository;
    private final StaffMemberRepository staffMemberRepository;
    private final LoginAttemptService loginAttemptService;
    private final CookieService cookieService;
    private final AuthService authService;

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

    /** Mascara o identificador do usuário nos logs (mantém os 2 primeiros chars). */
    private String maskUsername(String username) {
        if (username == null || username.isEmpty()) {
            return "***";
        }
        return username.length() <= 2 ? "***" : username.substring(0, 2) + "***";
    }

    // ─── Endpoints ───────────────────────────────────────────────────────────

    @PostMapping("/signin")
    public ResponseEntity<?> authenticateUser(
            @Valid @RequestBody LoginRequest loginRequest,
            HttpServletRequest request,
            HttpServletResponse response) {

        String ipAddress = extractClientIp(request);

        logger.info("[AUTH API] [SIGNIN] Tentativa de login: {}", maskUsername(loginRequest.getUsername()));

        try {
            com.portalcursos.ng02.dto.JwtResponse jwtResponse = authService.login(
                    loginRequest.getUsername(), loginRequest.getPassword(), ipAddress, request, response);

            logger.info("[AUTH API] [SUCCESS] Usuário {} autenticado com sucesso.", maskUsername(loginRequest.getUsername()));
            return ResponseEntity.ok(jwtResponse);

        } catch (AccountLockedException e) {
            logger.warn("[SECURITY] Tentativa de login bloqueada para IP: {}", ipAddress);
            return ResponseEntity
                    .status(org.springframework.http.HttpStatus.LOCKED)
                    .body(new MessageResponse(e.getMessage()));
        } catch (org.springframework.security.core.AuthenticationException e) {
            logger.warn("[AUTH API] [FAILURE] Falha na autenticação para {}: {}", maskUsername(loginRequest.getUsername()), e.getMessage());
            return ResponseEntity
                    .status(org.springframework.http.HttpStatus.UNAUTHORIZED)
                    .body(new MessageResponse("Erro de Autenticação: Usuário ou senha inválidos."));
        } catch (Exception e) {
            logger.error("[AUTH API] [ERROR] Erro inesperado no login para {}: {}", maskUsername(loginRequest.getUsername()), e.getMessage());
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
        String refreshToken = cookieService.extractRefreshToken(request, bodyToken);

        if (refreshToken == null || refreshToken.isBlank()) {
            return ResponseEntity.badRequest().body(new MessageResponse("Refresh token não fornecido."));
        }

        logger.debug("[AUTH] Renovação de token solicitada.");

        try {
            String username = authService.refreshToken(refreshToken, rateLimitKey, response);
            logger.info("[AUTH] Token renovado para: {}", maskUsername(username));
            // Retorna apenas confirmação — tokens viajam via cookie
            return ResponseEntity.ok(new MessageResponse("Token renovado com sucesso."));
        } catch (com.portalcursos.ng02.exception.SessionExpiredException e) {
            logger.warn("[AUTH] Refresh token expirado.");
            return ResponseEntity.status(403).body(new MessageResponse(e.getMessage()));
        } catch (com.portalcursos.ng02.exception.InvalidSessionException e) {
            logger.warn("[AUTH] Refresh token não encontrado.");
            return ResponseEntity.status(403).body(new MessageResponse(e.getMessage()));
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
        Optional<StaffMember> staff = staffMemberRepository.findByIdAndActiveTrue(userDetails.getId());
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
        String refreshToken = cookieService.extractRefreshToken(request, bodyToken);

        logger.info("[AUTH] Encerrando sessão.");

        if (refreshToken != null && !refreshToken.isBlank()) {
            userSessionRepository.findByRefreshToken(refreshToken)
                    .ifPresent(session -> {
                        userSessionRepository.delete(session);
                        logger.info("[AUTH] Sessão removida do banco.");
                    });
        }

        cookieService.clearAuthCookies(response);
        return ResponseEntity.ok(new MessageResponse("Logout realizado com sucesso."));
    }

    @PostMapping("/signup")
    public ResponseEntity<?> registerUser(
            @Valid @RequestBody SignupRequest signUpRequest,
            HttpServletRequest request) {
        logger.info("[AUTH] [SIGNUP] Tentativa de registro: {}", maskUsername(signUpRequest.getUsername()));

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

        try {
            authService.signup(signUpRequest);
            logger.info("[AUTH] [SIGNUP-SUCCESS] Usuário {} registrado.", maskUsername(signUpRequest.getUsername()));
            return ResponseEntity.ok(new MessageResponse("Usuário registrado com sucesso."));

        } catch (com.portalcursos.ng02.exception.SignupPrivilegeException e) {
            logger.warn("[SECURITY] Tentativa de registro com roles privilegiadas bloqueada: {}", maskUsername(signUpRequest.getUsername()));
            return ResponseEntity.status(403).body(new MessageResponse(e.getMessage()));
        } catch (com.portalcursos.ng02.exception.SignupConflictException e) {
            return ResponseEntity.badRequest().body(new MessageResponse(e.getMessage()));
        } catch (com.portalcursos.ng02.exception.BusinessException e) {
            // Role desconhecida — delega ao GlobalExceptionHandler para manter o formato padrão (400).
            throw e;
        } catch (Exception e) {
            logger.error("[AUTH] [SIGNUP-ERROR] Falha ao salvar usuário: ", e);
            return ResponseEntity
                    .status(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new MessageResponse("Erro interno ao criar usuário. Tente novamente."));
        }
    }
}
