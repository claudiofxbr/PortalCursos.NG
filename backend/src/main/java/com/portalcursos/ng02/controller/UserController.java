package com.portalcursos.ng02.controller;

import com.portalcursos.ng02.model.User;
import com.portalcursos.ng02.service.StorageService;
import com.portalcursos.ng02.service.UserService;
import com.portalcursos.ng02.dto.MessageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Set;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final StorageService storageService;

    @GetMapping
    @PreAuthorize("hasAnyRole('ROOT_MASTER', 'ADMIN', 'COORDENADOR', 'SECRETARIA')")
    public ResponseEntity<?> getAllUsers() {
        return ResponseEntity.ok(userService.getAllUsers());
    }

    @PostMapping(consumes = {"multipart/form-data"})
    @PreAuthorize("hasAnyRole('ROOT_MASTER', 'ADMIN', 'COORDENADOR', 'SECRETARIA')")
    public ResponseEntity<?> createUser(
            @RequestParam("username") String username,
            @RequestParam("email") String email,
            @RequestParam("password") String password,
            @RequestParam("roles") Set<String> roles,
            @RequestParam(value = "fullName", required = false) String fullName,
            @RequestParam(value = "position", required = false) String position,
            @RequestParam(value = "department", required = false) String department,
            @RequestParam(value = "foto3x4File", required = false) MultipartFile foto3x4File
    ) throws java.io.IOException {
        String fotoPath = null;
        if (foto3x4File != null && !foto3x4File.isEmpty()) {
            fotoPath = storageService.store(foto3x4File, "staff-photos");
        }

        User user = User.builder()
                .username(username)
                .email(email)
                .password(password)
                .fotoUrl(fotoPath)
                .build();

        return ResponseEntity.ok(userService.createUser(user, roles, fullName, position, department));
    }

    @PutMapping("/{id}/roles")
    @PreAuthorize("hasAnyRole('ROOT_MASTER', 'ADMIN', 'COORDENADOR', 'SECRETARIA')")
    public ResponseEntity<?> updateUserRoles(@PathVariable Long id, @RequestBody Set<String> roles) {
        return ResponseEntity.ok(userService.updateUserRoles(id, roles));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ROOT_MASTER', 'ADMIN', 'COORDENADOR', 'SECRETARIA')")
    public ResponseEntity<?> deleteUser(@PathVariable Long id) {
        userService.deleteUser(id);
        return ResponseEntity.ok(new MessageResponse("Usuário removido com sucesso"));
    }
}
