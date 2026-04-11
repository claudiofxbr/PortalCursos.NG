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
                .creatorName(ticket.getCreatorName())
                .creatorPosition(ticket.getCreatorPosition())
                .creatorPhotoUrl(ticket.getCreatorPhotoUrl())
                .build();
    }

    @GetMapping({"", "/tickets"})
    @PreAuthorize("hasAnyRole('STUDENT', 'TEACHER', 'STAFF', 'ADMIN')")
    public List<RepairTicketDTO> getAllTickets() {
        logger.info("[REPAIR API] Buscando todos os tickets de reparo...");
        return repairRepository.findAll().stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    @PostMapping(value = {"", "/tickets"}, consumes = {"multipart/form-data"})
    @PreAuthorize("hasAnyRole('STUDENT', 'TEACHER', 'STAFF', 'ADMIN')")
    public ResponseEntity<?> createTicket(
            @RequestParam("title") String title,
            @RequestParam("description") String description,
            @RequestParam("location") String location,
            @RequestParam(value = "mainPhotoFile", required = false) MultipartFile mainPhotoFile
    ) {
        logger.info("[REPAIR API] Iniciando criação de ticket: {}", title);
        
        RepairTicket ticket = new RepairTicket();
        ticket.setTitle(title);
        ticket.setDescription(description);
        ticket.setLocation(location);
        ticket.setStatus(RepairTicket.ERepairStatus.OPEN);

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String username = authentication.getName();
        
        Optional<User> user = userRepository.findByUsername(username);
        if (user.isPresent()) {
            ticket.setReportedBy(user.get());
        }

        if (mainPhotoFile != null && !mainPhotoFile.isEmpty()) {
            try {
                String photoPath = storageService.store(mainPhotoFile, "repairs-main");
                ticket.setMainPhotoUrl(photoPath);
            } catch (Exception e) {
                logger.error("[REPAIR API] Erro ao salvar foto principal", e);
                return ResponseEntity.status(500).body("Erro ao processar imagem.");
            }
        }

        // --- [AUDITORIA V30.9-SUPREME] ---
        // Quem está gerando a cobrança (ou ticket) é o funcionário logado
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (principal instanceof UserDetailsImpl) {
            UserDetailsImpl userDetails = (UserDetailsImpl) principal;
            staffMemberRepository.findByUserId(userDetails.getId()).ifPresent(staff -> {
                ticket.setCreatorName(staff.getFullName());
                ticket.setCreatorPosition(staff.getPosition());
                ticket.setCreatorPhotoUrl(staff.getFotoUrl());
            });
        }

        RepairTicket savedTicket = repairRepository.save(ticket);
        logger.info("[REPAIR API] Ticket ID {} salvo com sucesso.", savedTicket.getId());
        return ResponseEntity.ok(convertToDTO(savedTicket));
    }

    @PostMapping("/{id}/photo")
    @PreAuthorize("hasAnyRole('STUDENT', 'TEACHER', 'STAFF', 'ADMIN')")
    public ResponseEntity<?> uploadPhoto(@PathVariable @NonNull Long id, @RequestParam("file") MultipartFile file) {
        logger.info("[REPAIR API] Upload de foto para ticket ID: {}", id);
        Optional<RepairTicket> ticketOptional = repairRepository.findById(id);
        
        if (ticketOptional.isPresent()) {
            RepairTicket t = ticketOptional.get();
            if (t.getPhotoUrls().size() >= 4) {
                logger.warn("[REPAIR API] Limite de fotos atingido para o ticket {}", id);
                return ResponseEntity.badRequest().body("Limite de 4 fotos por ticket atingido.");
            }
            
            // Simulação de salvamento de arquivo (Em produção usaria S3 ou similar)
            String fileName = "repair_" + id + "_" + System.currentTimeMillis() + "_" + file.getOriginalFilename();
            t.getPhotoUrls().add("/uploads/repairs/" + fileName);
            repairRepository.save(t);
            
            logger.info("[REPAIR API] Foto adicionada com sucesso ao ticket {}", id);
            return ResponseEntity.ok(convertToDTO(t));
        }
        
        logger.error("[REPAIR API] Tentativa de upload para ticket inexistente: {}", id);
        return ResponseEntity.notFound().build();
    }

    @PutMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('STAFF', 'ADMIN')")
    public ResponseEntity<?> updateStatus(@PathVariable @NonNull Long id, @RequestParam("status") String status) {
        return repairRepository.findById(id).map(ticket -> {
            ticket.setStatus(RepairTicket.ERepairStatus.valueOf(status.toUpperCase()));
            
            // Auditoria de quem alterou o status
            Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
            if (principal instanceof UserDetailsImpl) {
                UserDetailsImpl userDetails = (UserDetailsImpl) principal;
                staffMemberRepository.findByUserId(userDetails.getId()).ifPresent(staff -> {
                    ticket.setCreatorName(staff.getFullName());
                    ticket.setCreatorPosition(staff.getPosition());
                    ticket.setCreatorPhotoUrl(staff.getFotoUrl());
                });
            }

            return ResponseEntity.ok(convertToDTO(repairRepository.save(ticket)));
        }).orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('STAFF', 'ADMIN')")
    public ResponseEntity<?> deleteTicket(@PathVariable @NonNull Long id) {
        return repairRepository.findById(id).map(ticket -> {
            // Soft Delete automatizado via Hibernate @SQLDelete
            repairRepository.delete(ticket);
            return ResponseEntity.ok().build();
        }).orElse(ResponseEntity.notFound().build());
    }
}
