package com.portalcursos.ng02.service;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Arrays;

/**
 * Encapsula a criação/limpeza dos cookies HttpOnly de autenticação e a extração
 * do refresh token. Antes vivia inline no {@code AuthController} (achado 4.1 da
 * auditoria — controller grande com responsabilidade de infra). Comportamento
 * idêntico ao original (mesmos atributos: HttpOnly, Secure, SameSite=Strict,
 * paths e maxAge).
 */
@Service
public class CookieService {

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

    /** Lifetime do refresh token / sessão — usado também para a expiryDate da UserSession. */
    public long getRefreshExpirationMs() {
        return refreshExpirationMs;
    }

    public Cookie buildAccessCookie(String token) {
        Cookie cookie = new Cookie(accessCookieName, token);
        cookie.setHttpOnly(true);
        cookie.setSecure(secureCookies);
        cookie.setPath("/");
        cookie.setMaxAge(jwtExpirationMs / 1000);
        cookie.setAttribute("SameSite", "Strict");
        return cookie;
    }

    public Cookie buildRefreshCookie(String token) {
        Cookie cookie = new Cookie(refreshCookieName, token);
        cookie.setHttpOnly(true);
        cookie.setSecure(secureCookies);
        cookie.setPath("/api/auth/refreshtoken");
        cookie.setMaxAge((int) (refreshExpirationMs / 1000));
        cookie.setAttribute("SameSite", "Strict");
        return cookie;
    }

    /** Seta cookies de expiração imediata para realizar o logout. */
    public void clearAuthCookies(HttpServletResponse response) {
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

    /** Extrai o refresh token do cookie ou, como fallback, do body da requisição. */
    public String extractRefreshToken(HttpServletRequest request, String bodyToken) {
        if (request.getCookies() != null) {
            return Arrays.stream(request.getCookies())
                    .filter(c -> refreshCookieName.equals(c.getName()))
                    .map(Cookie::getValue)
                    .findFirst()
                    .orElse(bodyToken);
        }
        return bodyToken;
    }
}
