package com.portalcursos.ng02.controller;

import com.portalcursos.ng02.dto.ChargePriceDtos.*;
import com.portalcursos.ng02.dto.MessageResponse;
import com.portalcursos.ng02.service.ChargePriceService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * Tabela de valores das cobranças. O {@code @PreAuthorize} é só a primeira barreira; o escopo por curso e a
 * diferença entre administrador, chefe e secretaria são decididos em {@link ChargePriceService}.
 */
@RestController
@RequestMapping("/api/finance")
@RequiredArgsConstructor
public class ChargePriceController {

    private static final String ADMIN = "hasAnyRole('ADMIN', 'ROOT_MASTER')";
    private static final String ADMIN_OR_SECRETARIA = "hasAnyRole('ADMIN', 'ROOT_MASTER', 'SECRETARIA')";

    private final ChargePriceService service;

    @GetMapping("/prices")
    @PreAuthorize(ADMIN_OR_SECRETARIA)
    public ResponseEntity<TableDto> list() {
        return ResponseEntity.ok(service.list());
    }

    @GetMapping("/prices/{id}/history")
    @PreAuthorize(ADMIN_OR_SECRETARIA)
    public ResponseEntity<?> history(@PathVariable Long id) {
        return ResponseEntity.ok(service.history(id));
    }

    @PutMapping("/prices/{id}")
    @PreAuthorize(ADMIN)
    public ResponseEntity<RowDto> setValue(@PathVariable Long id, @RequestBody SetValueRequest request) {
        return ResponseEntity.ok(service.setValue(id, request));
    }

    @PostMapping("/prices/course-rows")
    @PreAuthorize(ADMIN)
    public ResponseEntity<RowDto> createCourseRow(@RequestBody CourseRowRequest request) {
        return ResponseEntity.status(201).body(service.createCourseRow(request));
    }

    @PostMapping("/prices/{id}/proposals")
    @PreAuthorize(ADMIN_OR_SECRETARIA)
    public ResponseEntity<RowDto> propose(@PathVariable Long id, @RequestBody ProposeRequest request) {
        return ResponseEntity.status(201).body(service.propose(id, request));
    }

    @PostMapping("/prices/proposals/{changeId}/approve")
    @PreAuthorize(ADMIN)
    public ResponseEntity<RowDto> approve(@PathVariable Long changeId) {
        return ResponseEntity.ok(service.approve(changeId));
    }

    @PostMapping("/prices/proposals/{changeId}/reject")
    @PreAuthorize(ADMIN)
    public ResponseEntity<RowDto> reject(@PathVariable Long changeId, @RequestBody(required = false) DecisionRequest request) {
        return ResponseEntity.ok(service.reject(changeId, request));
    }

    // Vínculo colaborador-curso e marcação de chefe (só administrador).
    @GetMapping("/price-scopes")
    @PreAuthorize(ADMIN)
    public ResponseEntity<?> listScopes() {
        return ResponseEntity.ok(service.listScopes());
    }

    @PostMapping("/price-scopes")
    @PreAuthorize(ADMIN)
    public ResponseEntity<ScopeDto> saveScope(@RequestBody ScopeRequest request) {
        return ResponseEntity.ok(service.saveScope(request));
    }

    @DeleteMapping("/price-scopes/{id}")
    @PreAuthorize(ADMIN)
    public ResponseEntity<?> deleteScope(@PathVariable Long id) {
        service.deleteScope(id);
        return ResponseEntity.ok(new MessageResponse("Vínculo removido"));
    }
}
