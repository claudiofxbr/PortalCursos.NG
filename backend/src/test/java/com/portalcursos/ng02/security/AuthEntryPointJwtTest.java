package com.portalcursos.ng02.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.BadCredentialsException;

/** B8 da auditoria: o 401 não repassa a mensagem interna do Spring Security. */
class AuthEntryPointJwtTest {

    @Test
    void respondeComMensagemFixaSemVazarDetalheDaExcecao() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setServletPath("/api/algo");

        new AuthEntryPointJwt().commence(request, response, new BadCredentialsException("detalhe-interno-secreto"));

        String body = response.getContentAsString();
        assertEquals(401, response.getStatus());
        assertTrue(body.contains("Não autenticado"));
        assertFalse(body.contains("detalhe-interno-secreto"));
    }
}
