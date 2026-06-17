package com.portalcursos.ng02.controller;

import com.portalcursos.ng02.model.StaffMember;
import com.portalcursos.ng02.repository.StaffMemberRepository;
import com.portalcursos.ng02.service.AuditService;
import com.portalcursos.ng02.service.StorageService;
import com.portalcursos.ng02.dto.MessageResponse;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Optional;

@RestController
@RequestMapping("/api/v1/staff")
@RequiredArgsConstructor
public class StaffMemberController {

    private static final Logger logger = LoggerFactory.getLogger(StaffMemberController.class);

    private final StaffMemberRepository staffRepository;
    private final StorageService storageService;
    private final AuditService auditService;

    @GetMapping
    @PreAuthorize("hasRole('ADMIN') or hasRole('ROOT_MASTER')")
    public ResponseEntity<?> getAllStaff() {
        return ResponseEntity.ok(staffRepository.findAll());
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

        String fotoPath = null;
        if (foto3x4File != null && !foto3x4File.isEmpty()) {
            try {
                fotoPath = storageService.store(foto3x4File, "staff-photos");
            } catch (Exception e) {
                logger.error("[STAFF API] Erro ao salvar foto institucional: {}", e.getMessage());
            }
        }

        StaffMember staff = StaffMember.builder()
                .fullName(fullName)
                .position(position)
                .department(department)
                .fotoUrl(fotoPath)
                .build();

        auditService.injectCreator(staff);

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
        Optional<StaffMember> staffOpt = staffRepository.findById(id);
        if (staffOpt.isEmpty()) {
            return ResponseEntity.status(404).body(new MessageResponse("Membro não encontrado."));
        }

        StaffMember staff = staffOpt.get();
        staff.setFullName(fullName);
        staff.setPosition(position);
        staff.setDepartment(department);

        if (foto3x4File != null && !foto3x4File.isEmpty()) {
            try {
                storageService.delete(staff.getFotoUrl());
                String fotoPath = storageService.store(foto3x4File, "staff-photos");
                staff.setFotoUrl(fotoPath);
            } catch (Exception e) {
                logger.error("[STAFF API] Erro ao atualizar foto institucional: {}", e.getMessage());
            }
        }

        auditService.injectCreator(staff);

        StaffMember updated = staffRepository.save(staff);
        return ResponseEntity.ok(updated);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('ROOT_MASTER')")
    public ResponseEntity<?> deleteStaff(@PathVariable Long id) {
        Optional<StaffMember> staffOpt = staffRepository.findById(id);
        if (staffOpt.isEmpty()) {
            return ResponseEntity.status(404).body(new MessageResponse("Membro não encontrado para remoção."));
        }

        StaffMember staff = staffOpt.get();
        try {
            storageService.delete(staff.getFotoUrl());
        } catch (Exception e) {
            logger.warn("[STAFF API] Erro ao deletar arquivo de foto: {}", e.getMessage());
        }
        staffRepository.delete(staff);
        return ResponseEntity.ok(new MessageResponse("Membro removido com sucesso."));
    }
}
