package com.portalcursos.ng02.controller;

import com.portalcursos.ng02.model.User;
import com.portalcursos.ng02.service.StorageService;
import com.portalcursos.ng02.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Set;
import com.portalcursos.ng02.dto.MessageResponse;

@CrossOrigin(origins = "*", maxAge = 3600)
@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    @Autowired
    private UserService userService;

    @Autowired
    private StorageService storageService;

    @GetMapping
    @PreAuthorize("hasRole('ROOT_MASTER') or hasRole('ADMIN') or hasRole('COORDENADOR') or hasRole('SECRETARIA')")
    public ResponseEntity<?> getAllUsers() {
        try {
            return ResponseEntity.ok(userService.getAllUsers());
        } catch (Exception e) {
            System.err.println("[SUPREME-ERROR] Erro ao listar usuários: " + e.getMessage());
            return ResponseEntity.internalServerError().body(new MessageResponse("Erro ao listar usuários: " + e.getMessage()));
        }
    }

    @PostMapping(consumes = {"multipart/form-data"})
    @PreAuthorize("hasRole('ROOT_MASTER') or hasRole('ADMIN') or hasRole('COORDENADOR') or hasRole('SECRETARIA')")
    public ResponseEntity<?> createUser(
            @RequestParam("username") String username,
            @RequestParam("email") String email,
            @RequestParam("password") String password,
            @RequestParam("roles") Set<String> roles,
            @RequestParam(value = "fullName", required = false) String fullName,
            @RequestParam(value = "position", required = false) String position,
            @RequestParam(value = "department", required = false) String department,
            @RequestParam(value = "foto3x4File", required = false) MultipartFile foto3x4File
    ) {
        try {
            String fotoPath = null;
            if (foto3x4File != null && !foto3x4File.isEmpty()) {
                try {
                    fotoPath = storageService.store(foto3x4File, "staff-photos");
                } catch (Exception e) {
                    System.err.println("[SUPREME-ERROR] Erro no upload de foto: " + e.getMessage());
                    return ResponseEntity.badRequest().body(new MessageResponse("Erro no upload da foto: " + e.getMessage()));
                }
            }

            User user = User.builder()
                    .username(username)
                    .email(email)
                    .password(password)
                    .fotoUrl(fotoPath)
                    .build();
                     
            return ResponseEntity.ok(userService.createUser(user, roles, fullName, position, department));
        } catch (Exception e) {
            System.err.println("[SUPREME-ERROR] Erro ao criar usuário: " + e.getMessage());
            return ResponseEntity.internalServerError().body(new MessageResponse("Erro ao criar usuário: " + e.getMessage()));
        }
    }

    @PutMapping("/{id}/roles")
    @PreAuthorize("hasRole('ROOT_MASTER') or hasRole('ADMIN') or hasRole('COORDENADOR') or hasRole('SECRETARIA')")
    public ResponseEntity<?> updateUserRoles(@PathVariable Long id, @RequestBody Set<String> roles) {
        try {
            return ResponseEntity.ok(userService.updateUserRoles(id, roles));
        } catch (Exception e) {
            System.err.println("[SUPREME-ERROR] Erro ao atualizar roles: " + e.getMessage());
            return ResponseEntity.internalServerError().body(new MessageResponse("Erro ao atualizar permissões: " + e.getMessage()));
        }
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ROOT_MASTER') or hasRole('ADMIN') or hasRole('COORDENADOR') or hasRole('SECRETARIA')")
    public ResponseEntity<?> deleteUser(@PathVariable Long id) {
        try {
            userService.deleteUser(id);
            return ResponseEntity.ok(new MessageResponse("Usuário removido com sucesso"));
        } catch (Exception e) {
            System.err.println("[SUPREME-ERROR] Erro ao remover usuário ID " + id + ": " + e.getMessage());
            return ResponseEntity.internalServerError().body(new MessageResponse("Erro ao remover usuário: " + e.getMessage()));
        }
    }

    @lombok.Data
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class UserRequest {
        private String username;
        private String email;
        private String password;
        private Set<String> roles;
    }
}
