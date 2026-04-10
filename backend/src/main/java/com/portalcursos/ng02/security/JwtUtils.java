package com.portalcursos.ng02.security;

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
    private int jwtRefreshExpirationMs;

    public String generateTokenFromUsername(String username) {
        return Jwts.builder()
                .setSubject(username)
                .setIssuedAt(new Date())
                .setExpiration(new Date((new Date()).getTime() + jwtExpirationMs))
                .signWith(key(), SignatureAlgorithm.HS256)
                .compact();
    }

    private Key key() {
        return Keys.hmacShaKeyFor(Decoders.BASE64.decode(jwtSecret));
    }

    public String getUserNameFromJwtToken(String token) {
        return Jwts.parserBuilder().setSigningKey(key()).build()
                .parseClaimsJws(token).getBody().getSubject();
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
