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
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.time.Instant;
import java.time.temporal.ChronoUnit;


import com.portalcursos.ng02.model.UserSession;
import com.portalcursos.ng02.repository.UserSessionRepository;
import org.springframework.security.authentication.LockedException;
import lombok.RequiredArgsConstructor;
import com.portalcursos.ng02.repository.StaffMemberRepository;
import com.portalcursos.ng02.service.LoginAttemptService;
import com.portalcursos.ng02.model.StaffMember;
import java.util.Optional;


@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {
    private static final Logger logger = LoggerFactory.getLogger(AuthController.class);

    private final AuthenticationManager authenticationManager;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder encoder;
    private final JwtUtils jwtUtils;
    private final UserSessionRepository userSessionRepository;
    private final StaffMemberRepository staffMemberRepository;
    private final LoginAttemptService loginAttemptService;

    @org.springframework.beans.factory.annotation.Value("${portalcursos.jwt.refresh-expiration:604800000}")
    private Long refreshExpirationMs;

    @PostMapping("/signin")
    public ResponseEntity<?> authenticateUser(@Valid @RequestBody LoginRequest loginRequest, HttpServletRequest request) {
        String ipAddress = request.getHeader("X-Forwarded-For");
        if (ipAddress == null || ipAddress.isEmpty()) {
            ipAddress = request.getRemoteAddr();
        }

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

            // --- [ROBUSTEZ: SESSÃO ÚNICA] ---
            userSessionRepository.deleteByUser(user);
            
            // Sucesso no login: Resetar tentativas
            loginAttemptService.loginSucceeded(ipAddress);

            // 1. Gerar Tokens
            String jwt = jwtUtils.generateTokenFromUserDetails(userDetails);
            String refreshTokenStr = UUID.randomUUID().toString();

            // 2. Persistir Sessão
            UserSession session = UserSession.builder()
                    .user(user)
                    .refreshToken(refreshTokenStr)
                    .expiryDate(Instant.now().plusMillis(refreshExpirationMs))
                    .userAgent(request.getHeader("User-Agent") != null ? request.getHeader("User-Agent") : "Unknown")
                    .ipAddress(ipAddress)
                    .build();
            userSessionRepository.save(session);

            List<String> roles = userDetails.getAuthorities().stream()
                    .map(item -> item.getAuthority())
                    .collect(Collectors.toList());

            logger.info("[AUTH API] [SUCCESS] Usuário {} autenticado com sucesso.", loginRequest.getUsername());

            return ResponseEntity.ok(new com.portalcursos.ng02.dto.JwtResponse(jwt, refreshTokenStr, userDetails.getId(),
                    userDetails.getUsername(), userDetails.getEmail(), roles));

        } catch (org.springframework.security.core.AuthenticationException e) {
            loginAttemptService.loginFailed(ipAddress);
            logger.error("[AUTH API] [FAILURE] Falha na autenticação para {}: {}", loginRequest.getUsername(), e.getMessage());
            return ResponseEntity
                    .status(org.springframework.http.HttpStatus.UNAUTHORIZED)
                    .body(new MessageResponse("Erro de Autenticação: Usuário ou senha inválidos. Restam " + loginAttemptService.getRemainingAttempts(ipAddress) + " tentativas."));
        } catch (Exception e) {
            logger.error("[AUTH API] [ERROR] Erro inesperado no login para {}: {}", loginRequest.getUsername(), e.getMessage());
            return ResponseEntity
                    .status(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new MessageResponse("Erro interno no servidor de autenticação."));
        }
    }

    @PostMapping("/refreshtoken")
    @org.springframework.transaction.annotation.Transactional
    public ResponseEntity<?> refreshtoken(@Valid @RequestBody com.portalcursos.ng02.dto.TokenRefreshRequest request) {
        String refreshToken = request.getRefreshToken();
        logger.info("[SUPREME-AUTH] Solicitando renovação de token para: ...{}", 
            refreshToken != null && refreshToken.length() > 5 ? refreshToken.substring(refreshToken.length() - 5) : "INVALID");

        try {
            if ((refreshToken != null) && (refreshToken.length() > 0)) {
                return userSessionRepository.findByRefreshToken(refreshToken)
                        .map(session -> {
                            // Verificar expiração
                            if (session.getExpiryDate().isBefore(Instant.now())) {
                                userSessionRepository.delete(session);
                                logger.warn("[SUPREME-AUTH] Refresh token expirado.");
                                return ResponseEntity.status(403).body(new MessageResponse("Refresh token expirado. Faça login novamente."));
                            }

                            User user = session.getUser();
                            String token = jwtUtils.generateTokenFromUser(user);
                            
                            logger.info("[SUPREME-AUTH] Token renovado com sucesso para o usuário: {}", user.getUsername());
                            return ResponseEntity.ok(new com.portalcursos.ng02.dto.TokenRefreshResponse(token, refreshToken));
                        })
                        .orElseGet(() -> {
                            logger.warn("[SUPREME-AUTH] Refresh token não encontrado no banco.");
                            return ResponseEntity.status(403).body(new MessageResponse("Refresh token não encontrado no banco."));
                        });
            }
            return ResponseEntity.badRequest().body(new MessageResponse("Refresh Token é obrigatório."));
        } catch (Exception e) {
            logger.error("[SUPREME-AUTH] Erro crítico no refresh token: ", e);
            return ResponseEntity.status(500).body(new MessageResponse("Erro interno ao processar renovação de token. Protocolo V30.9."));
        }
    }

    @GetMapping("/me")
    public ResponseEntity<?> getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        
        if (authentication == null || !authentication.isAuthenticated() || authentication.getPrincipal().equals("anonymousUser")) {
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
    public ResponseEntity<?> logoutUser(@Valid @RequestBody com.portalcursos.ng02.dto.TokenRefreshRequest request) {
        String refreshToken = request.getRefreshToken();
        logger.info("[AUTH API] [SIGNOUT] Iniciando encerramento de sessão.");
        
        if (refreshToken != null && !refreshToken.isEmpty()) {
            userSessionRepository.findByRefreshToken(refreshToken)
                .ifPresent(session -> {
                    userSessionRepository.delete(session);
                    logger.info("[AUTH API] [SIGNOUT] Sessão encerrada para o token finalizado em: ...{}", 
                        refreshToken.substring(Math.max(0, refreshToken.length() - 5)));
                });
        }

        return ResponseEntity.ok(new MessageResponse("Logout realizado com sucesso. Protocolo V30.0-SUPREME."));
    }

    @PostMapping("/signup")
    public ResponseEntity<?> registerUser(@Valid @RequestBody SignupRequest signUpRequest) {
        logger.info("[OMEGA-AUTH] [SIGNUP] Tentativa de registro: {} ({})", 
            signUpRequest.getUsername(), signUpRequest.getEmail());

        Set<String> requestedRoles = signUpRequest.getRole();
        boolean isRequestingPrivilegedRoles = requestedRoles != null && requestedRoles.stream()
            .anyMatch(role -> role.equalsIgnoreCase("admin") 
                           || role.equalsIgnoreCase("staff") 
                           || role.equalsIgnoreCase("teacher"));

        if (isRequestingPrivilegedRoles) {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            boolean hasElevatedPrivileges = false;
            if (auth != null && auth.isAuthenticated() && !(auth.getPrincipal() instanceof String && auth.getPrincipal().equals("anonymousUser"))) {
                hasElevatedPrivileges = auth.getAuthorities().stream()
                    .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN") || a.getAuthority().equals("ROLE_ROOT_MASTER"));
            }
            
            if (!hasElevatedPrivileges) {
                logger.warn("[SECURITY-BREACH-ATTEMPT] Tentativa de registro de usuário com papéis privilegiados bloqueada para: {}", signUpRequest.getUsername());
                return ResponseEntity.status(403)
                        .body(new MessageResponse("Erro: Apenas administradores do sistema podem registrar contas privilegiadas (admin, staff, teacher)."));
            }
        }

        if (userRepository.existsByUsername(signUpRequest.getUsername())) {
            logger.warn("[OMEGA-AUTH] [SIGNUP-FAILURE] Username já em uso: {}", signUpRequest.getUsername());
            return ResponseEntity
                    .badRequest()
                    .body(new MessageResponse("Erro: O nome de usuário já está sendo utilizado!"));
        }

        if (userRepository.existsByEmail(signUpRequest.getEmail())) {
            logger.warn("[OMEGA-AUTH] [SIGNUP-FAILURE] Email já em uso: {}", signUpRequest.getEmail());
            return ResponseEntity
                    .badRequest()
                    .body(new MessageResponse("Erro: O endereço de email já está sendo utilizado!"));
        }

        try {
            // Create new user's account
            User user = User.builder()
                    .username(signUpRequest.getUsername())
                    .email(signUpRequest.getEmail())
                    .password(encoder.encode(signUpRequest.getPassword()))
                    .build();

            Set<String> strRoles = signUpRequest.getRole();
            Set<Role> roles = new HashSet<>();

            if (strRoles == null || strRoles.isEmpty()) {
                Role userRole = roleRepository.findByName(Role.ERole.ROLE_ALUNO)
                        .orElseThrow(() -> new RuntimeException("Erro: Papel ROLE_ALUNO não encontrado no banco."));
                roles.add(userRole);
            } else {
                strRoles.forEach(role -> {
                    switch (role.toLowerCase()) {
                        case "admin":
                            Role adminRole = roleRepository.findByName(Role.ERole.ROLE_ADMIN)
                                    .orElseThrow(() -> new RuntimeException("Erro: Papel ROLE_ADMIN não encontrado."));
                            roles.add(adminRole);
                            break;
                        case "staff":
                            Role modRole = roleRepository.findByName(Role.ERole.ROLE_SECRETARIA)
                                    .orElseThrow(() -> new RuntimeException("Erro: Papel ROLE_SECRETARIA não encontrado."));
                            roles.add(modRole);
                            break;
                        case "teacher":
                            Role teacherRole = roleRepository.findByName(Role.ERole.ROLE_PROFESSOR)
                                    .orElseThrow(() -> new RuntimeException("Erro: Papel ROLE_PROFESSOR não encontrado."));
                            roles.add(teacherRole);
                            break;
                        default:
                            Role userRole = roleRepository.findByName(Role.ERole.ROLE_ALUNO)
                                    .orElseThrow(() -> new RuntimeException("Erro: Papel padrão não encontrado."));
                            roles.add(userRole);
                    }
                });
            }

            user.setRoles(roles);
            userRepository.save(user);

            logger.info("[OMEGA-AUTH] [SIGNUP-SUCCESS] Usuário {} registrado com sucesso via Hostinger.", signUpRequest.getUsername());
            return ResponseEntity.ok(new MessageResponse("Usuário registrado com sucesso no Protocolo OMEGA!"));
            
        } catch (Exception e) {
            logger.error("[OMEGA-AUTH] [SIGNUP-ERROR] Falha crítica ao salvar usuário: ", e);
            return ResponseEntity
                    .status(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new MessageResponse("Erro interno no banco de dados Neon ao criar usuário. Tente novamente em instantes."));
        }
    }
}
