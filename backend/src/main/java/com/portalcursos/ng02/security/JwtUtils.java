package com.portalcursos.ng02.security;

import com.portalcursos.ng02.service.UserDetailsImpl;
import io.jsonwebtoken.*;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SignatureException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.security.Key;
import java.util.Date;

@Component
public class JwtUtils {
    private static final Logger logger = LoggerFactory.getLogger(JwtUtils.class);

    @Value("${portalcursos.jwt.secret}")
    private String jwtSecret;

    @Value("${portalcursos.jwt.expiration}")
    private int jwtExpirationMs;

    @Value("${portalcursos.jwt.refresh-expiration}")
    private long jwtRefreshExpirationMs;

    public String generateTokenFromUserDetails(UserDetailsImpl userDetails) {
        java.util.List<String> roles = userDetails.getAuthorities().stream()
                .map(org.springframework.security.core.GrantedAuthority::getAuthority)
                .collect(java.util.stream.Collectors.toList());

        return Jwts.builder()
                .setSubject(userDetails.getUsername())
                .claim("id", userDetails.getId())
                .claim("email", userDetails.getEmail())
                .claim("roles", roles)
                .setIssuedAt(new Date())
                .setExpiration(new Date((new Date()).getTime() + jwtExpirationMs))
                .signWith(key(), SignatureAlgorithm.HS256)
                .compact();
    }

    public String generateTokenFromUser(com.portalcursos.ng02.model.User user) {
        java.util.List<String> roles = user.getRoles().stream()
                .map(role -> role.getName().name())
                .collect(java.util.stream.Collectors.toList());

        return Jwts.builder()
                .setSubject(user.getUsername())
                .claim("id", user.getId())
                .claim("email", user.getEmail())
                .claim("roles", roles)
                .setIssuedAt(new Date())
                .setExpiration(new Date((new Date()).getTime() + jwtExpirationMs))
                .signWith(key(), SignatureAlgorithm.HS256)
                .compact();
    }

    public Claims getClaimsFromJwtToken(String token) {
        return Jwts.parserBuilder().setSigningKey(key()).build()
                .parseClaimsJws(token).getBody();
    }

    private Key key() {
        return Keys.hmacShaKeyFor(Decoders.BASE64.decode(jwtSecret));
    }

    public boolean validateJwtToken(String authToken) {
        try {
            Jwts.parserBuilder().setSigningKey(key()).build().parseClaimsJws(authToken);
            return true;
        } catch (SignatureException e) {
            logger.error("[JWT ERROR] Assinatura inválida: {}", e.getMessage());
        } catch (MalformedJwtException e) {
            logger.error("[JWT ERROR] Token malformado: {}", e.getMessage());
        } catch (ExpiredJwtException e) {
            logger.error("[JWT ERROR] Token expirado: {}", e.getMessage());
        } catch (UnsupportedJwtException e) {
            logger.error("[JWT ERROR] Token não suportado: {}", e.getMessage());
        } catch (IllegalArgumentException e) {
            logger.error("[JWT ERROR] Claims vazias ou nulas: {}", e.getMessage());
        } catch (Exception e) {
            logger.error("[JWT ERROR] Falha inesperada: {}", e.getMessage());
        }

        return false;
    }
}
