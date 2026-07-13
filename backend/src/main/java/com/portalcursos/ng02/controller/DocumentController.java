package com.portalcursos.ng02.controller;

import com.portalcursos.ng02.service.StorageService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Substitui o antigo resource handler estático de "/uploads/**" (que era
 * público, sem autenticação — qualquer pessoa com a URL/UUID acessava
 * documentos de alunos, RG, diplomas, fotos). Cada categoria de arquivo
 * exige o mesmo conjunto de roles já usado pelo controller "dono" daquele
 * tipo de dado (GradStudentController, PostgradStudentController,
 * RepairController, StaffMemberController), preservando o modelo de acesso
 * existente sem exigir refatoração de DTOs.
 */
@RestController
@RequestMapping("/api/uploads")
@RequiredArgsConstructor
public class DocumentController {

    private static final Logger logger = LoggerFactory.getLogger(DocumentController.class);

    private static final String REPAIR_ROLES =
            "hasAnyRole('ROOT_MASTER', 'ADMIN', 'SECRETARIA', 'FINANCEIRO', 'ACADEMICO', 'COORDENADOR', 'PROFESSOR')";
    private static final String STUDENT_DOC_ROLES =
            "hasAnyRole('ADMIN', 'SECRETARIA', 'ACADEMICO', 'MATRICULA', 'ROOT_MASTER')";
    private static final String STAFF_PHOTO_ROLES = "isAuthenticated()";

    private final StorageService storageService;

    @GetMapping("/**")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Resource> serve(jakarta.servlet.http.HttpServletRequest request) {
        String relativePath = extractRelativePath(request);
        String category = firstSegment(relativePath);

        if (!isAuthorizedFor(category)) {
            logger.warn("[SECURITY-ALERT] Acesso negado a arquivo de categoria '{}': usuário sem role permitida", category);
            throw new AccessDeniedException("Sem permissão para acessar este arquivo.");
        }

        try {
            Resource resource = storageService.loadAsResource(relativePath);
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .body(resource);
        } catch (IOException e) {
            return ResponseEntity.notFound().build();
        }
    }

    private boolean isAuthorizedFor(String category) {
        var auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return false;
        }
        var expression = switch (category) {
            case "repairs-main", "repairs-gallery", "repairs" -> REPAIR_ROLES;
            case "grad-students", "postgrad", "documents" -> STUDENT_DOC_ROLES;
            case "staff-photos", "fotos-perfil" -> STAFF_PHOTO_ROLES;
            default -> null;
        };
        if (expression == null) {
            return false;
        }
        if (STAFF_PHOTO_ROLES.equals(expression)) {
            return true;
        }
        return auth.getAuthorities().stream().anyMatch(a -> expression.contains(a.getAuthority()));
    }

    private String extractRelativePath(jakarta.servlet.http.HttpServletRequest request) {
        String path = (String) request.getAttribute(
                org.springframework.web.servlet.HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE);
        return UriUtils.decode(path, StandardCharsets.UTF_8);
    }

    private String firstSegment(String relativePath) {
        int idx = relativePath.indexOf('/');
        return idx == -1 ? relativePath : relativePath.substring(0, idx);
    }
}
