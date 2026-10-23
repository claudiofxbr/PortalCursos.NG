package com.portalcursos.ng02.controller;

import com.portalcursos.ng02.service.PixConfigCheck;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Estado da configuração do Pix, só para administrador: situação, ambiente (sandbox ou produção) e as variáveis do
 * servidor que precisam de ajuste. Nunca devolve valores de token ou segredo (só o nome da variável).
 */
@RestController
@RequestMapping("/api/finance")
@RequiredArgsConstructor
public class PixConfigController {

    private final PixConfigCheck pixConfigCheck;

    @GetMapping("/pix-config")
    @PreAuthorize("hasAnyRole('ADMIN', 'ROOT_MASTER')")
    public ResponseEntity<PixConfigCheck.Report> pixConfig() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(pixConfigCheck.check());
    }
}
