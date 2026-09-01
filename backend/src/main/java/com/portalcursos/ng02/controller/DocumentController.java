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
import java.util.Set;

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

    private static final Set<String> REPAIR_ROLES = Set.of(
            "ROLE_ROOT_MASTER", "ROLE_ADMIN", "ROLE_SECRETARIA", "ROLE_FINANCEIRO", "ROLE_ACADEMICO", "ROLE_COORDENADOR", "ROLE_PROFESSOR");
    private static final Set<String> STUDENT_DOC_ROLES = Set.of(
            "ROLE_ADMIN", "ROLE_SECRETARIA", "ROLE_ACADEMICO", "ROLE_MATRICULA", "ROLE_ROOT_MASTER");

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
        if (category.equals("staff-photos") || category.equals("fotos-perfil")) {
            return true; // qualquer usuário autenticado
        }
        Set<String> allowedRoles = switch (category) {
            case "repairs-main", "repairs-gallery", "repairs" -> REPAIR_ROLES;
            case "grad-students", "postgrad", "documents" -> STUDENT_DOC_ROLES;
            default -> Set.of();
        };
        return auth.getAuthorities().stream().anyMatch(a -> allowedRoles.contains(a.getAuthority()));
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
