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


@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private static final Logger logger = LoggerFactory.getLogger(AuthController.class);

    @Autowired
    AuthenticationManager authenticationManager;

    @Autowired
    UserRepository userRepository;

    @Autowired
    RoleRepository roleRepository;

    @Autowired
    PasswordEncoder encoder;

    @Autowired
    JwtUtils jwtUtils;

    @Autowired
    UserSessionRepository userSessionRepository;

    @PostMapping("/signin")
    public ResponseEntity<?> authenticateUser(@Valid @RequestBody LoginRequest loginRequest) {
        logger.info("[AUTH API] [SIGNIN] Tentativa de login: {}", loginRequest.getUsername());

        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(loginRequest.getUsername(), loginRequest.getPassword()));

            SecurityContextHolder.getContext().setAuthentication(authentication);
            
            UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal();
            User user = userRepository.findById(userDetails.getId())
                    .orElseThrow(() -> new RuntimeException("Erro: Usuário não encontrado."));

            // 1. Gerar Tokens
            String jwt = jwtUtils.generateTokenFromUsername(userDetails.getUsername());
            String refreshTokenStr = UUID.randomUUID().toString();

            // 2. Persistir Sessão no Banco
            UserSession session = UserSession.builder()
                    .user(user)
                    .refreshToken(refreshTokenStr)
                    .expiryDate(Instant.now().plus(7, ChronoUnit.DAYS)) // 7 dias
                    .userAgent("Web Browser")
                    .ipAddress("0.0.0.0")
                    .build();
            userSessionRepository.save(session);

            List<String> roles = userDetails.getAuthorities().stream()
                    .map(item -> item.getAuthority())
                    .collect(Collectors.toList());

            logger.info("[AUTH API] [SUCCESS] Usuário {} autenticado com sucesso. Sessão criada.", loginRequest.getUsername());

            return ResponseEntity.ok(new com.portalcursos.ng02.dto.JwtResponse(jwt, refreshTokenStr, userDetails.getId(),
                    userDetails.getUsername(), userDetails.getEmail(), roles));
        } catch (org.springframework.security.core.AuthenticationException e) {
            logger.error("[AUTH API] [FAILURE] Falha na autenticação para {}: {}", loginRequest.getUsername(), e.getMessage());
            return ResponseEntity
                    .status(org.springframework.http.HttpStatus.UNAUTHORIZED)
                    .body(new MessageResponse("Erro de Autenticação: Usuário ou senha inválidos."));
        } catch (Exception e) {
            logger.error("[AUTH API] [ERROR] Erro inesperado no login: ", e);
            return ResponseEntity
                    .status(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new MessageResponse("Erro interno no servidor de autenticação."));
        }
    }

    @PostMapping("/refreshtoken")
    public ResponseEntity<?> refreshtoken(@Valid @RequestBody com.portalcursos.ng02.dto.TokenRefreshRequest request) {
        String refreshToken = request.getRefreshToken();

        if ((refreshToken != null) && (refreshToken.length() > 0)) {
            return userSessionRepository.findByRefreshToken(refreshToken)
                    .map(session -> {
                        // Verificar expiração
                        if (session.getExpiryDate().isBefore(Instant.now())) {
                            userSessionRepository.delete(session);
                            return ResponseEntity.status(403).body(new MessageResponse("Refresh token expirado. Faça login novamente."));
                        }

                        User user = session.getUser();
                        String token = jwtUtils.generateTokenFromUsername(user.getUsername());

                        return ResponseEntity.ok(new com.portalcursos.ng02.dto.TokenRefreshResponse(token, refreshToken));
                    })
                    .orElse(ResponseEntity.status(403).body(new MessageResponse("Refresh token não encontrado no banco.")));
        }

        return ResponseEntity.badRequest().body(new MessageResponse("Refresh Token é obrigatório."));
    }

    @GetMapping("/me")
    public ResponseEntity<?> getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        
        if (authentication == null || !authentication.isAuthenticated() || authentication.getPrincipal().equals("anonymousUser")) {
            return ResponseEntity.status(401).body(new MessageResponse("Não autenticado."));
        }

        UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal();

        return ResponseEntity.ok(new com.portalcursos.ng02.dto.UserInfoResponse(
                userDetails.getId(),
                userDetails.getUsername(),
                userDetails.getEmail(),
                userDetails.getAuthorities().stream()
                        .map(item -> item.getAuthority())
                        .collect(Collectors.toList())));
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
