package com.portalcursos.ng02.controller;

import com.portalcursos.ng02.dto.RepairTicketDTO;
import com.portalcursos.ng02.model.RepairTicket;
import com.portalcursos.ng02.model.StaffMember;
import com.portalcursos.ng02.repository.RepairRepository;
import com.portalcursos.ng02.repository.StaffMemberRepository;
import com.portalcursos.ng02.service.StorageService;
import com.portalcursos.ng02.service.UserDetailsImpl;
import com.portalcursos.ng02.dto.MessageResponse;
import com.portalcursos.ng02.service.LoginAttemptService;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.NonNull;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.stream.Collectors;

@CrossOrigin(origins = "*", maxAge = 3600)
@RestController
@RequestMapping("/api/repairs")
@RequiredArgsConstructor
public class RepairController {

    private static final Logger logger = LoggerFactory.getLogger(RepairController.class);

    private final RepairRepository repairRepository;
    private final StaffMemberRepository staffMemberRepository;
    private final StorageService storageService;

    private void injectAuditStamps(Object entity) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof UserDetailsImpl) {
            UserDetailsImpl userDetails = (UserDetailsImpl) auth.getPrincipal();
            staffMemberRepository.findById(userDetails.getId()).ifPresent(staff -> {
                if (entity instanceof com.portalcursos.ng02.model.BaseAuditEntity) {
                    ((com.portalcursos.ng02.model.BaseAuditEntity) entity).setCreator(staff);
                }
            });
        }
    }

    private RepairTicketDTO convertToDTO(RepairTicket ticket) {
        if (ticket == null) return null;
        
        RepairTicketDTO dto = RepairTicketDTO.builder()
                .id(ticket.getId())
                .title(ticket.getTitle())
                .description(ticket.getDescription())
                .location(ticket.getLocation())
                .status(ticket.getStatus() != null ? ticket.getStatus() : RepairTicket.ERepairStatus.OPEN)
                .photoUrls(ticket.getPhotoUrls() != null ? ticket.getPhotoUrls() : new java.util.ArrayList<>())
                .mainPhotoUrl(ticket.getMainPhotoUrl())
                .createdAt(ticket.getCreatedAt())
                .build();

        // Popula metadados de auditoria a partir do objeto StaffMember normalizado (3FN)
        StaffMember auditor = ticket.getCreator();
        if (auditor != null) {
            dto.setCreatorName(auditor.getFullName());
            dto.setCreatorPosition(auditor.getPosition());
            dto.setCreatorPhotoUrl(auditor.getFotoUrl());
        } else {
            dto.setCreatorName("Auditor do Sistema");
            dto.setCreatorPosition("EQUIPE TÉCNICA");
        }

        return dto;
    }

    // --- PROTOCOLO DE ACESSO V37.7-SUPREME ---
    private static final String AUTHORIZED_ROLES = "hasAnyRole('ROOT_MASTER', 'ADMIN', 'SECRETARIA', 'FINANCEIRO', 'ACADEMICO', 'COORDENADOR', 'PROFESSOR')";

    @GetMapping({"", "/tickets"})
    @PreAuthorize(AUTHORIZED_ROLES)
    @Transactional(readOnly = true)
    public ResponseEntity<?> getAllTickets() {
        logger.info("[CAMPUS-CARE] Sincronizando chamados para auditoria autorizada.");
        List<RepairTicketDTO> tickets = repairRepository.findAll().stream()
                .map(this::convertToDTO)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toList());
        return ResponseEntity.ok(tickets);
    }

    @PostMapping(value = {"", "/tickets"}, consumes = {"multipart/form-data"})
    @Transactional
    @PreAuthorize(AUTHORIZED_ROLES)
    public ResponseEntity<?> createTicket(
            @RequestParam("title") String title,
            @RequestParam("description") String description,
            @RequestParam("location") String location,
            @RequestParam(value = "mainPhotoFile", required = false) MultipartFile mainPhotoFile
    ) {
        logger.info("[CAMPUS-CARE] Registrando novo incidente: {}", title);
        
        RepairTicket ticket = new RepairTicket();
        ticket.setTitle(title);
        ticket.setDescription(description);
        ticket.setLocation(location);
        ticket.setStatus(RepairTicket.ERepairStatus.OPEN);

        // Sincronização Biométrica do Auditor normalizada
        injectAuditStamps(ticket);

        if (mainPhotoFile != null && !mainPhotoFile.isEmpty()) {
            try {
                String photoPath = storageService.store(mainPhotoFile, "repairs-main");
                ticket.setMainPhotoUrl(photoPath);
            } catch (Exception e) {
                logger.error("[STORAGE-ERROR] Falha ao salvar foto principal: {}", e.getMessage());
                return ResponseEntity.badRequest().body(new MessageResponse("Erro ao salvar imagem: " + e.getMessage()));
            }
        }

        RepairTicket savedTicket = repairRepository.save(ticket);
        return ResponseEntity.ok(convertToDTO(savedTicket));
    }

    @PostMapping("/{id}/photo")
    @Transactional
    @PreAuthorize(AUTHORIZED_ROLES)
    public ResponseEntity<?> uploadPhoto(@PathVariable @NonNull Long id, @RequestParam("file") MultipartFile file) {
        RepairTicket t = repairRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Chamado não localizado."));
        
        if (t.getPhotoUrls().size() >= 4) {
            return ResponseEntity.badRequest().body(new MessageResponse("Limite de evidências visuais atingido (4 fotos)."));
        }
        
        try {
            String photoPath = storageService.store(file, "repairs-gallery");
            t.getPhotoUrls().add(photoPath);
            return ResponseEntity.ok(convertToDTO(repairRepository.save(t)));
        } catch (Exception e) {
            logger.error("[STORAGE-ERROR] Falha ao adicionar foto à galeria: {}", e.getMessage());
            return ResponseEntity.badRequest().body(new MessageResponse("Erro ao salvar evidência: " + e.getMessage()));
        }
    }

    @PutMapping("/{id}/status")
    @Transactional
    @PreAuthorize(AUTHORIZED_ROLES)
    public ResponseEntity<?> updateStatus(@PathVariable @NonNull Long id, @RequestParam("status") String status) {
        RepairTicket ticket = repairRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Chamado não localizado."));

        try {
            ticket.setStatus(RepairTicket.ERepairStatus.valueOf(status.toUpperCase()));
            // Re-sincroniza auditor responsável pela alteração de status
            injectAuditStamps(ticket);
            return ResponseEntity.ok(convertToDTO(repairRepository.save(ticket)));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(new MessageResponse("Status inválido."));
        }
    }

    @DeleteMapping("/{id}")
    @Transactional
    @PreAuthorize(AUTHORIZED_ROLES)
    public ResponseEntity<?> deleteTicket(@PathVariable @NonNull Long id) {
        RepairTicket ticket = repairRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Chamado não localizado."));
        
        // Limpeza de arquivos relacionados antes de deletar o registro
        if (ticket.getMainPhotoUrl() != null) {
            storageService.delete(ticket.getMainPhotoUrl());
        }
        if (ticket.getPhotoUrls() != null) {
            ticket.getPhotoUrls().forEach(storageService::delete);
        }

        repairRepository.delete(ticket);
        return ResponseEntity.ok(new MessageResponse("Chamado removido e evidências deletadas. Protocolo OMEGA."));
    }
}
