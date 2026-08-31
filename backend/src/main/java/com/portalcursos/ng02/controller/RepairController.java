package com.portalcursos.ng02.controller;

import com.portalcursos.ng02.dto.RepairTicketDTO;
import com.portalcursos.ng02.model.RepairTicket;
import com.portalcursos.ng02.model.StaffMember;
import com.portalcursos.ng02.repository.RepairRepository;
import com.portalcursos.ng02.service.AuditService;
import com.portalcursos.ng02.service.StorageService;
import com.portalcursos.ng02.exception.ResourceNotFoundException;
import com.portalcursos.ng02.dto.MessageResponse;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.NonNull;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/repairs")
@RequiredArgsConstructor
public class RepairController {

    private static final Logger logger = LoggerFactory.getLogger(RepairController.class);

    private final RepairRepository repairRepository;
    private final StorageService storageService;
    private final AuditService auditService;

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
        List<RepairTicketDTO> tickets = repairRepository.findAllWithCreator().stream()
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
        auditService.injectCreator(ticket);

        if (mainPhotoFile != null && !mainPhotoFile.isEmpty()) {
            try {
                String photoPath = storageService.store(mainPhotoFile, "repairs-main");
                ticket.setMainPhotoUrl(photoPath);
            } catch (Exception e) {
                logger.error("[STORAGE-ERROR] Falha ao salvar foto principal.", e);
                throw new com.portalcursos.ng02.exception.BusinessException("Erro ao salvar imagem. Tente novamente.");
            }
        }

        RepairTicket savedTicket = repairRepository.save(ticket);
        return ResponseEntity.ok(convertToDTO(savedTicket));
    }

    @PostMapping("/{id}/photo")
    @Transactional
    @PreAuthorize(AUTHORIZED_ROLES)
    public ResponseEntity<?> uploadPhoto(@PathVariable @NonNull Long id, @RequestParam("file") MultipartFile file) {
        RepairTicket t = repairRepository.findByIdAndActiveTrue(id)
                .orElseThrow(() -> new ResourceNotFoundException("Chamado não localizado ou inativo."));
        
        if (t.getPhotoUrls().size() >= 4) {
            return ResponseEntity.badRequest().body(new MessageResponse("Limite de evidências visuais atingido (4 fotos)."));
        }
        
        try {
            String photoPath = storageService.store(file, "repairs-gallery");
            t.getPhotoUrls().add(photoPath);
            return ResponseEntity.ok(convertToDTO(repairRepository.save(t)));
        } catch (Exception e) {
            logger.error("[STORAGE-ERROR] Falha ao adicionar foto à galeria.", e);
            throw new com.portalcursos.ng02.exception.BusinessException("Erro ao salvar evidência. Tente novamente.");
        }
    }

    @PutMapping("/{id}/status")
    @Transactional
    @PreAuthorize(AUTHORIZED_ROLES)
    public ResponseEntity<?> updateStatus(@PathVariable @NonNull Long id, @RequestParam("status") String status) {
        RepairTicket ticket = repairRepository.findByIdAndActiveTrue(id)
                .orElseThrow(() -> new ResourceNotFoundException("Chamado não localizado ou inativo."));

        try {
            ticket.setStatus(RepairTicket.ERepairStatus.valueOf(status.toUpperCase()));
            // O creator do ticket é sempre quem abriu o chamado — não deve ser
            // sobrescrito por quem apenas altera o status posteriormente.
            return ResponseEntity.ok(convertToDTO(repairRepository.save(ticket)));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(new MessageResponse("Status inválido."));
        }
    }

    @DeleteMapping("/{id}")
    @Transactional
    @PreAuthorize(AUTHORIZED_ROLES)
    public ResponseEntity<?> deleteTicket(@PathVariable @NonNull Long id) {
        RepairTicket ticket = repairRepository.findByIdAndActiveTrue(id)
                .orElseThrow(() -> new ResourceNotFoundException("Chamado não localizado ou inativo."));
        
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
