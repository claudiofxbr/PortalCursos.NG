package com.portalcursos.ng02.controller;

import com.portalcursos.ng02.dto.RepairTicketDTO;
import com.portalcursos.ng02.model.RepairTicket;
import com.portalcursos.ng02.model.User;
import com.portalcursos.ng02.repository.RepairRepository;
import com.portalcursos.ng02.repository.UserRepository;
import com.portalcursos.ng02.service.StorageService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import com.portalcursos.ng02.model.StaffMember;
import com.portalcursos.ng02.repository.StaffMemberRepository;
import com.portalcursos.ng02.service.UserDetailsImpl;
import org.springframework.lang.NonNull;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import com.portalcursos.ng02.dto.MessageResponse;
import org.springframework.transaction.annotation.Transactional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@CrossOrigin(origins = "*", maxAge = 3600)
@RestController
@RequestMapping("/api/repairs")
public class RepairController {

    private static final Logger logger = LoggerFactory.getLogger(RepairController.class);

    @Autowired
    RepairRepository repairRepository;

    @Autowired
    UserRepository userRepository;

    @Autowired
    StaffMemberRepository staffMemberRepository;

    @Autowired
    StorageService storageService;

    private RepairTicketDTO convertToDTO(RepairTicket ticket) {
        return RepairTicketDTO.builder()
                .id(ticket.getId())
                .title(ticket.getTitle())
                .description(ticket.getDescription())
                .location(ticket.getLocation())
                .status(ticket.getStatus())
                .photoUrls(ticket.getPhotoUrls())
                .mainPhotoUrl(ticket.getMainPhotoUrl())
                .createdAt(ticket.getCreatedAt())
                .reportedByFullName(ticket.getReportedBy() != null ? ticket.getReportedBy().getUsername() : "Anônimo")
                .creatorName(ticket.getReportedByName())
                .creatorPosition(ticket.getReportedByRole())
                .creatorPhotoUrl(ticket.getReporterPhotoUrl())
                .photoUrls(ticket.getPhotoUrls() != null ? ticket.getPhotoUrls() : new java.util.ArrayList<>())
                .build();
    }

    // --- PROTOCOLO DE ACESSO V37.7-SUPREME ---
    // Apenas funcionários de alto escalão e corpo docente podem gerenciar infraestrutura
    private static final String AUTHORIZED_ROLES = "hasAnyRole('ROOT_MASTER', 'ADMIN', 'SECRETARIA', 'FINANCEIRO', 'ACADEMICO', 'COORDENADOR', 'PROFESSOR')";

    @GetMapping({"", "/tickets"})
    @PreAuthorize(AUTHORIZED_ROLES)
    @Transactional(readOnly = true)
    public ResponseEntity<?> getAllTickets() {
        try {
            logger.info("[CAMPUS-CARE] Sincronizando chamados para auditoria autorizada.");
            List<RepairTicketDTO> tickets = repairRepository.findAll().stream()
                    .map(this::convertToDTO)
                    .collect(Collectors.toList());
            return ResponseEntity.ok(tickets);
        } catch (Exception e) {
            String rootCause = e.getCause() != null ? e.getCause().getMessage() : e.getMessage();
            logger.error("[SUPREME-ERR] Falha crítica na listagem de reparos: {}. Causa: {}", e.getMessage(), rootCause);
            return ResponseEntity.internalServerError().body(new MessageResponse("Erro ao carregar infraestrutura de reparos. Diagnóstico: " + rootCause));
        }
    }

    @PostMapping(value = {"", "/tickets"}, consumes = {"multipart/form-data"})
    @PreAuthorize(AUTHORIZED_ROLES)
    public ResponseEntity<?> createTicket(
            @RequestParam("title") String title,
            @RequestParam("description") String description,
            @RequestParam("location") String location,
            @RequestParam(value = "mainPhotoFile", required = false) MultipartFile mainPhotoFile
    ) {
        try {
            logger.info("[CAMPUS-CARE] Registrando novo incidente: {}", title);
            
            RepairTicket ticket = new RepairTicket();
            ticket.setTitle(title);
            ticket.setDescription(description);
            ticket.setLocation(location);
            ticket.setStatus(RepairTicket.ERepairStatus.OPEN);

            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            String username = authentication.getName();
            
            Optional<User> user = userRepository.findByUsername(username);
            user.ifPresent(ticket::setReportedBy);

            // Sincronização Biométrica do Auditor baseada no StaffMember
            Object principal = authentication.getPrincipal();
            if (principal instanceof UserDetailsImpl) {
                UserDetailsImpl userDetails = (UserDetailsImpl) principal;
                staffMemberRepository.findById(userDetails.getId()).ifPresent(staff -> {
                    ticket.setReportedByName(staff.getFullName());
                    ticket.setReportedByRole(staff.getPosition());
                    ticket.setReporterPhotoUrl(staff.getFotoUrl());
                });
            }

            // Fallback Crítico: Se não houver dados de staff, usa dados do User base
            if (ticket.getReportedByName() == null && user.isPresent()) {
                ticket.setReportedByName(user.get().getUsername());
                ticket.setReportedByRole("USUÁRIO AUTORIZADO");
                ticket.setReporterPhotoUrl("default-auditor.png");
            }

            if (mainPhotoFile != null && !mainPhotoFile.isEmpty()) {
                String photoPath = storageService.store(mainPhotoFile, "repairs-main");
                ticket.setMainPhotoUrl(photoPath);
            }

            RepairTicket savedTicket = repairRepository.save(ticket);
            return ResponseEntity.ok(convertToDTO(savedTicket));
        } catch (Exception e) {
            String rootCause = e.getCause() != null ? e.getCause().getMessage() : e.getMessage();
            logger.error("[SUPREME-ERR] Falha crítica ao registrar chamado: {}. Causa: {}", e.getMessage(), rootCause);
            
            // Verificação proativa de colunas (Diagnostic)
            if (rootCause != null && rootCause.contains("column")) {
                logger.warn("[DIAGNOSTIC] Possível ausência de colunas de auditoria na tabela repair_tickets. Favor executar V38.0-CAMPUS-CARE-DB-FIX.sql");
            }

            return ResponseEntity.internalServerError().body(new MessageResponse("Falha sistêmica ao registrar incidente. Verifique se o banco de dados está atualizado com o protocolo V38.0."));
        }
    }

    @PostMapping("/{id}/photo")
    @PreAuthorize(AUTHORIZED_ROLES)
    public ResponseEntity<?> uploadPhoto(@PathVariable @NonNull Long id, @RequestParam("file") MultipartFile file) {
        try {
            Optional<RepairTicket> ticketOptional = repairRepository.findById(id);
            if (ticketOptional.isPresent()) {
                RepairTicket t = ticketOptional.get();
                if (t.getPhotoUrls().size() >= 4) {
                    return ResponseEntity.badRequest().body(new MessageResponse("Limite de evidências visuais atingido (4 fotos)."));
                }
                
                String photoPath = storageService.store(file, "repairs-gallery");
                t.getPhotoUrls().add(photoPath);
                return ResponseEntity.ok(convertToDTO(repairRepository.save(t)));
            }
            return ResponseEntity.status(404).body(new MessageResponse("Chamado não localizado."));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(new MessageResponse("Erro no processamento da imagem."));
        }
    }

    @PutMapping("/{id}/status")
    @PreAuthorize(AUTHORIZED_ROLES)
    public ResponseEntity<?> updateStatus(@PathVariable @NonNull Long id, @RequestParam("status") String status) {
        try {
            Optional<RepairTicket> ticketOpt = repairRepository.findById(id);
            if (ticketOpt.isEmpty()) return ResponseEntity.notFound().build();

            RepairTicket ticket = ticketOpt.get();
            ticket.setStatus(RepairTicket.ERepairStatus.valueOf(status.toUpperCase()));
            
            // Re-sincroniza auditor responsável pela alteração de status
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth.getPrincipal() instanceof UserDetailsImpl) {
                UserDetailsImpl details = (UserDetailsImpl) auth.getPrincipal();
                staffMemberRepository.findById(details.getId()).ifPresent(staff -> {
                    ticket.setReportedByName(staff.getFullName());
                    ticket.setReportedByRole(staff.getPosition());
                    ticket.setReporterPhotoUrl(staff.getFotoUrl());
                });
            }

            return ResponseEntity.ok(convertToDTO(repairRepository.save(ticket)));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(new MessageResponse("Erro ao atualizar progresso do chamado."));
        }
    }

    @DeleteMapping("/{id}")
    @PreAuthorize(AUTHORIZED_ROLES)
    public ResponseEntity<?> deleteTicket(@PathVariable @NonNull Long id) {
        try {
            Optional<RepairTicket> ticketOpt = repairRepository.findById(id);
            if (ticketOpt.isEmpty()) return ResponseEntity.notFound().build();
            
            repairRepository.delete(ticketOpt.get());
            return ResponseEntity.ok(new MessageResponse("Chamado removido e arquivado para auditoria."));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(new MessageResponse("Erro ao processar remoção."));
        }
    }
}
