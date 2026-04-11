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

@CrossOrigin(origins = "*", maxAge = 3600)
@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    @Autowired
    private UserService userService;

    @Autowired
    private StorageService storageService;

    @GetMapping
    @PreAuthorize("hasRole('ROOT_MASTER') or hasRole('ADMIN')")
    public List<User> getAllUsers() {
        return userService.getAllUsers();
    }

    @PostMapping(consumes = {"multipart/form-data"})
    @PreAuthorize("hasRole('ROOT_MASTER') or hasRole('ADMIN')")
    public ResponseEntity<User> createUser(
            @RequestParam("username") String username,
            @RequestParam("email") String email,
            @RequestParam("password") String password,
            @RequestParam("roles") Set<String> roles,
            @RequestParam(value = "foto3x4File", required = false) MultipartFile foto3x4File
    ) {
        String fotoPath = null;
        if (foto3x4File != null && !foto3x4File.isEmpty()) {
            try {
                fotoPath = storageService.store(foto3x4File, "staff-photos");
            } catch (Exception e) {
                return ResponseEntity.internalServerError().build();
            }
        }

        User user = User.builder()
                .username(username)
                .email(email)
                .password(password)
                .fotoUrl(fotoPath)
                .build();
                 
        return ResponseEntity.ok(userService.createUser(user, roles));
    }

    @PutMapping("/{id}/roles")
    @PreAuthorize("hasRole('ROOT_MASTER') or hasRole('ADMIN')")
    public ResponseEntity<User> updateUserRoles(@PathVariable Long id, @RequestBody Set<String> roles) {
        return ResponseEntity.ok(userService.updateUserRoles(id, roles));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ROOT_MASTER') or hasRole('ADMIN')")
    public ResponseEntity<?> deleteUser(@PathVariable Long id) {
        userService.deleteUser(id);
        return ResponseEntity.ok().build();
    }

    // DTO para Criação
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
