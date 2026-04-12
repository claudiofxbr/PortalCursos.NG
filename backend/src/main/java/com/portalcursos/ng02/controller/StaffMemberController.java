package com.portalcursos.ng02.controller;

import com.portalcursos.ng02.model.StaffMember;
import com.portalcursos.ng02.repository.StaffMemberRepository;
import com.portalcursos.ng02.service.StorageService;
import com.portalcursos.ng02.service.UserDetailsImpl;
import org.springframework.security.core.context.SecurityContextHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Optional;

@CrossOrigin(origins = "*", maxAge = 3600)
@RestController
@RequestMapping("/api/v1/staff")
public class StaffMemberController {

    private static final Logger logger = LoggerFactory.getLogger(StaffMemberController.class);

    @Autowired
    private StaffMemberRepository staffRepository;

    @Autowired
    private StorageService storageService;

    @GetMapping
    @PreAuthorize("hasRole('ADMIN') or hasRole('ROOT_MASTER')")
    public ResponseEntity<?> getAllStaff() {
        try {
            return ResponseEntity.ok(staffRepository.findAll());
        } catch (Exception e) {
            System.err.println("[SUPREME-ERROR] Erro ao listar staff: " + e.getMessage());
            return ResponseEntity.internalServerError().body(new com.portalcursos.ng02.payload.response.MessageResponse("Erro ao carregar lista institucional."));
        }
    }

    @PostMapping(consumes = "multipart/form-data")
    @PreAuthorize("hasRole('ADMIN') or hasRole('ROOT_MASTER')")
    public ResponseEntity<?> createStaff(
            @RequestParam("fullName") String fullName,
            @RequestParam("position") String position,
            @RequestParam("department") String department,
            @RequestParam(value = "foto3x4File", required = false) MultipartFile foto3x4File
    ) {
        try {
            logger.info("[STAFF API] Criando novo membro institucional: {}", fullName);

            String fotoPath = null;
            if (foto3x4File != null && !foto3x4File.isEmpty()) {
                try {
                    fotoPath = storageService.store(foto3x4File, "staff-photos");
                } catch (Exception e) {
                    System.err.println("[SUPREME-ERROR] Erro ao salvar foto institucional: " + e.getMessage());
                }
            }

            Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
            String creatorName = "Sistema";
            String creatorPosition = "Automático";
            String creatorPhotoUrl = null;

            if (principal instanceof UserDetailsImpl) {
                UserDetailsImpl userDetails = (UserDetailsImpl) principal;
                Optional<StaffMember> creatorStaff = staffRepository.findByUserId(userDetails.getId());
                if (creatorStaff.isPresent()) {
                    creatorName = creatorStaff.get().getFullName();
                    creatorPosition = creatorStaff.get().getPosition();
                    creatorPhotoUrl = creatorStaff.get().getFotoUrl();
                }
            }

            StaffMember staff = StaffMember.builder()
                    .fullName(fullName)
                    .position(position)
                    .department(department)
                    .fotoUrl(fotoPath)
                    .creatorName(creatorName)
                    .creatorPosition(creatorPosition)
                    .creatorPhotoUrl(creatorPhotoUrl)
                    .build();

            StaffMember saved = staffRepository.save(staff);
            return ResponseEntity.ok(saved);
        } catch (Exception e) {
            System.err.println("[SUPREME-ERROR] Erro ao criar membro staff: " + e.getMessage());
            return ResponseEntity.internalServerError().body(new com.portalcursos.ng02.payload.response.MessageResponse("Erro ao cadastrar membro institucional."));
        }
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('ROOT_MASTER')")
    public ResponseEntity<?> updateStaff(
            @PathVariable Long id,
            @RequestParam("fullName") String fullName,
            @RequestParam("position") String position,
            @RequestParam("department") String department,
            @RequestParam(value = "foto3x4File", required = false) MultipartFile foto3x4File
    ) {
        try {
            return staffRepository.findById(id).map(staff -> {
                staff.setFullName(fullName);
                staff.setPosition(position);
                staff.setDepartment(department);

                if (foto3x4File != null && !foto3x4File.isEmpty()) {
                    try {
                        storageService.delete(staff.getFotoUrl());
                        String fotoPath = storageService.store(foto3x4File, "staff-photos");
                        staff.setFotoUrl(fotoPath);
                    } catch (Exception e) {
                        System.err.println("[SUPREME-ERROR] Erro ao atualizar foto institucional: " + e.getMessage());
                    }
                }

                Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
                if (principal instanceof UserDetailsImpl) {
                    UserDetailsImpl userDetails = (UserDetailsImpl) principal;
                    staffRepository.findByUserId(userDetails.getId()).ifPresent(creator -> {
                        staff.setCreatorName(creator.getFullName());
                        staff.setCreatorPosition(creator.getPosition());
                        staff.setCreatorPhotoUrl(creator.getFotoUrl());
                    });
                }

                StaffMember updated = staffRepository.save(staff);
                return ResponseEntity.ok(updated);
            }).orElse(ResponseEntity.status(404).body(new com.portalcursos.ng02.payload.response.MessageResponse("Membro não encontrado.")));
        } catch (Exception e) {
            System.err.println("[SUPREME-ERROR] Erro ao atualizar staff ID " + id + ": " + e.getMessage());
            return ResponseEntity.internalServerError().body(new com.portalcursos.ng02.payload.response.MessageResponse("Erro ao atualizar dados institucionais."));
        }
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('ROOT_MASTER')")
    public ResponseEntity<?> deleteStaff(@PathVariable Long id) {
        try {
            return staffRepository.findById(id).map(staff -> {
                try {
                    storageService.delete(staff.getFotoUrl());
                } catch (Exception e) {
                    System.err.println("[SUPREME-WARN] Erro ao deletar arquivo de foto: " + e.getMessage());
                }
                staffRepository.delete(staff);
                return ResponseEntity.ok(new com.portalcursos.ng02.payload.response.MessageResponse("Membro removido com sucesso."));
            }).orElse(ResponseEntity.status(404).body(new com.portalcursos.ng02.payload.response.MessageResponse("Membro não encontrado para remoção.")));
        } catch (Exception e) {
            System.err.println("[SUPREME-ERROR] Erro ao deletar staff ID " + id + ": " + e.getMessage());
            return ResponseEntity.internalServerError().body(new com.portalcursos.ng02.payload.response.MessageResponse("Erro ao remover membro institucional."));
        }
    }
}
