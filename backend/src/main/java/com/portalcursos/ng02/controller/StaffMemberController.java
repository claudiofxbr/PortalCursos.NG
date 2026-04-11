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
    public List<StaffMember> getAllStaff() {
        return staffRepository.findAll();
    }

    @PostMapping(consumes = "multipart/form-data")
    @PreAuthorize("hasRole('ADMIN') or hasRole('ROOT_MASTER')")
    public ResponseEntity<?> createStaff(
            @RequestParam("fullName") String fullName,
            @RequestParam("position") String position,
            @RequestParam("department") String department,
            @RequestParam(value = "foto3x4File", required = false) MultipartFile foto3x4File
    ) {
        logger.info("[STAFF API] Criando novo membro institucional: {}", fullName);



        // --- [AUDITORIA V30.9-SUPREME] ---
        String fotoPath = null;
        if (foto3x4File != null && !foto3x4File.isEmpty()) {
            try {
                fotoPath = storageService.store(foto3x4File, "staff-photos");
            } catch (Exception e) {
                logger.error("Erro ao salvar foto institucional", e);
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
                    logger.error("Erro ao atualizar foto institucional", e);
                }
            }

            // Atualizar auditoria para registrar quem editou pela última vez
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
        }).orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('ROOT_MASTER')")
    public ResponseEntity<?> deleteStaff(@PathVariable Long id) {
        return staffRepository.findById(id).map(staff -> {
            storageService.delete(staff.getFotoUrl());
            staffRepository.delete(staff);
            return ResponseEntity.ok().build();
        }).orElse(ResponseEntity.notFound().build());
    }
}
