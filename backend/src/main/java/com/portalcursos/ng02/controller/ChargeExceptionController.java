package com.portalcursos.ng02.controller;

import com.portalcursos.ng02.dto.ChargeExceptionDtos.DecisionRequest;
import com.portalcursos.ng02.dto.ChargeExceptionDtos.ExceptionDto;
import com.portalcursos.ng02.dto.ChargeExceptionDtos.ExceptionRequest;
import com.portalcursos.ng02.dto.ChargeExceptionDtos.PolicyDto;
import com.portalcursos.ng02.service.ChargeExceptionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Política de valores e pedidos de exceção. O {@code @PreAuthorize} é só a primeira barreira; o escopo por curso
 * e a regra "quem pediu não decide" são aplicados em {@link ChargeExceptionService}.
 */
@RestController
@RequestMapping("/api/finance")
@RequiredArgsConstructor
public class ChargeExceptionController {

    private static final String ADMIN = "hasAnyRole('ADMIN', 'ROOT_MASTER')";
    private static final String ADMIN_OR_SECRETARIA = "hasAnyRole('ADMIN', 'ROOT_MASTER', 'SECRETARIA')";

    private final ChargeExceptionService service;

    @GetMapping("/charge-policy")
    @PreAuthorize("hasAnyRole('ADMIN', 'ROOT_MASTER', 'SECRETARIA', 'FINANCEIRO')")
    public ResponseEntity<PolicyDto> policy() {
        return ResponseEntity.ok(service.policy());
    }

    @GetMapping("/charge-exceptions")
    @PreAuthorize(ADMIN_OR_SECRETARIA)
    public ResponseEntity<List<ExceptionDto>> list() {
        return ResponseEntity.ok(service.list());
    }

    @PostMapping("/charge-exceptions")
    @PreAuthorize(ADMIN_OR_SECRETARIA)
    public ResponseEntity<ExceptionDto> request(@RequestBody ExceptionRequest request) {
        return ResponseEntity.status(201).body(service.request(request));
    }

    @PostMapping("/charge-exceptions/{id}/approve")
    @PreAuthorize(ADMIN)
    public ResponseEntity<ExceptionDto> approve(@PathVariable Long id, @RequestBody(required = false) DecisionRequest request) {
        return ResponseEntity.ok(service.approve(id, request));
    }

    @PostMapping("/charge-exceptions/{id}/reject")
    @PreAuthorize(ADMIN)
    public ResponseEntity<ExceptionDto> reject(@PathVariable Long id, @RequestBody(required = false) DecisionRequest request) {
        return ResponseEntity.ok(service.reject(id, request));
    }
}
