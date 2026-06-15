package com.portalcursos.ng02.security;

import com.portalcursos.ng02.service.UserDetailsServiceImpl;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.NonNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Arrays;

public class AuthTokenFilter extends OncePerRequestFilter {
    @Autowired
    private JwtUtils jwtUtils;

    @Autowired
    private UserDetailsServiceImpl userDetailsService;

    @org.springframework.beans.factory.annotation.Value("${portalcursos.jwt.access-cookie-name:accessToken}")
    private String accessCookieName;

    private static final Logger logger = LoggerFactory.getLogger(AuthTokenFilter.class);

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull FilterChain filterChain)
            throws ServletException, IOException {
        String servletPath = request.getServletPath();
        String method = request.getMethod();
        
        // Protocolo V30.0-SUPREME: Bypass de logout para evitar travamentos de 401
        if ("/api/auth/signout".equals(servletPath)) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            String jwt = parseJwt(request);
            if (jwt != null) {
                if (jwtUtils.validateJwtToken(jwt)) {
                    io.jsonwebtoken.Claims claims = jwtUtils.getClaimsFromJwtToken(jwt);
                    String username = claims.getSubject();
                    
                    // Converte os tipos primitivos e genéricos obtidos das claims do JWT
                    Long id = claims.get("id", Long.class);
                    String email = claims.get("email", String.class);
                    @SuppressWarnings("unchecked")
                    java.util.List<String> roles = (java.util.List<String>) claims.get("roles");

                    if (roles == null) {
                        logger.warn("[FILTER WARN] Claims 'roles' ausente no token para: {}", username);
                        filterChain.doFilter(request, response);
                        return;
                    }

                    java.util.List<org.springframework.security.core.authority.SimpleGrantedAuthority> authorities =
                            roles.stream()
                                 .map(org.springframework.security.core.authority.SimpleGrantedAuthority::new)
                                 .collect(java.util.stream.Collectors.toList());
                    
                    UserDetails userDetails = new com.portalcursos.ng02.service.UserDetailsImpl(id, username, email, "", authorities);
                    
                    UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                            userDetails, null, userDetails.getAuthorities());
                    authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                    SecurityContextHolder.getContext().setAuthentication(authentication);
                    
                    logger.info("[FILTER SUCCESS] Usuário '{}' autenticado. Path: {} [{}] Roles: {}", 
                        username, servletPath, method, authentication.getAuthorities());
                } else {
                    logger.warn("[FILTER WARN] Token JWT inválido para path: {}", servletPath);
                }
            }
        } catch (Exception e) {
            logger.error("[FILTER ERROR] Falha crítica na filtragem: ", e);
        }

        filterChain.doFilter(request, response);
    }

    private String parseJwt(HttpServletRequest request) {
        // 1. Cookie HttpOnly tem prioridade (mais seguro — inacessível ao JavaScript)
        if (request.getCookies() != null) {
            String fromCookie = Arrays.stream(request.getCookies())
                    .filter(c -> accessCookieName.equals(c.getName()))
                    .map(Cookie::getValue)
                    .filter(StringUtils::hasText)
                    .findFirst()
                    .orElse(null);
            if (fromCookie != null) return fromCookie;
        }

        // 2. Fallback: header Authorization Bearer (clientes nativos/API sem cookies)
        String headerAuth = request.getHeader("Authorization");
        if (StringUtils.hasText(headerAuth) && headerAuth.startsWith("Bearer ")) {
            return headerAuth.substring(7);
        }

        return null;
    }
}
