package com.portalcursos.ng02.controller;

import com.portalcursos.ng02.model.StaffMember;
import com.portalcursos.ng02.repository.StaffMemberRepository;
import com.portalcursos.ng02.service.StorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

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



        String fotoPath = null;
        if (foto3x4File != null && !foto3x4File.isEmpty()) {
            try {
                fotoPath = storageService.store(foto3x4File, "staff-photos");
            } catch (Exception e) {
                logger.error("Erro ao salvar foto institucional", e);
                return ResponseEntity.internalServerError().body("Erro ao processar imagem.");
            }
        }

        StaffMember staff = StaffMember.builder()
                .fullName(fullName)
                .position(position)
                .department(department)
                .fotoUrl(fotoPath)
                .build();

        StaffMember saved = staffRepository.save(staff);
        return ResponseEntity.ok(saved);
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
