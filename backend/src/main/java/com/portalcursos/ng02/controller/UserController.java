package com.portalcursos.ng02.controller;

import com.portalcursos.ng02.model.User;
import com.portalcursos.ng02.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Set;

@CrossOrigin(origins = "*", maxAge = 3600)
@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    @Autowired
    private UserService userService;

    @GetMapping
    @PreAuthorize("hasRole('ROOT_MASTER') or hasRole('ADMIN')")
    public List<User> getAllUsers() {
        return userService.getAllUsers();
    }

    @PostMapping
    @PreAuthorize("hasRole('ROOT_MASTER') or hasRole('ADMIN')")
    public ResponseEntity<User> createUser(@RequestBody UserRequest userRequest) {
        User user = User.builder()
                .username(userRequest.getUsername())
                .email(userRequest.getEmail())
                .password(userRequest.getPassword())
                .build();
                
        return ResponseEntity.ok(userService.createUser(user, userRequest.getRoles()));
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
