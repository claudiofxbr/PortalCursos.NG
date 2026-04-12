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
    public ResponseEntity<?> getAllTickets() {
        try {
            logger.info("[REPAIR API] Buscando todos os tickets de reparo...");
            List<RepairTicketDTO> tickets = repairRepository.findAll().stream()
                    .map(this::convertToDTO)
                    .collect(Collectors.toList());
            return ResponseEntity.ok(tickets);
        } catch (Exception e) {
            System.err.println("[SUPREME-ERROR] Erro ao listar tickets: " + e.getMessage());
            return ResponseEntity.internalServerError().body(new com.portalcursos.ng02.payload.response.MessageResponse("Erro ao carregar tickets de reparo."));
        }
    }

    @PostMapping(value = {"", "/tickets"}, consumes = {"multipart/form-data"})
    @PreAuthorize("hasAnyRole('STUDENT', 'TEACHER', 'STAFF', 'ADMIN')")
    public ResponseEntity<?> createTicket(
            @RequestParam("title") String title,
            @RequestParam("description") String description,
            @RequestParam("location") String location,
            @RequestParam(value = "mainPhotoFile", required = false) MultipartFile mainPhotoFile
    ) {
        try {
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
                    System.err.println("[SUPREME-ERROR] Erro ao salvar foto do reparo: " + e.getMessage());
                }
            }

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
        } catch (Exception e) {
            System.err.println("[SUPREME-ERROR] Erro ao criar ticket ID: " + e.getMessage());
            return ResponseEntity.internalServerError().body(new com.portalcursos.ng02.payload.response.MessageResponse("Erro ao registrar ticket."));
        }
    }

    @PostMapping("/{id}/photo")
    @PreAuthorize("hasAnyRole('STUDENT', 'TEACHER', 'STAFF', 'ADMIN')")
    public ResponseEntity<?> uploadPhoto(@PathVariable @NonNull Long id, @RequestParam("file") MultipartFile file) {
        try {
            logger.info("[REPAIR API] Upload de foto para ticket ID: {}", id);
            Optional<RepairTicket> ticketOptional = repairRepository.findById(id);
            
            if (ticketOptional.isPresent()) {
                RepairTicket t = ticketOptional.get();
                if (t.getPhotoUrls().size() >= 4) {
                    return ResponseEntity.badRequest().body(new com.portalcursos.ng02.payload.response.MessageResponse("Limite de 4 fotos por ticket atingido."));
                }
                
                String photoPath = storageService.store(file, "repairs-gallery");
                t.getPhotoUrls().add(photoPath);
                repairRepository.save(t);
                
                return ResponseEntity.ok(convertToDTO(t));
            }
            return ResponseEntity.status(404).body(new com.portalcursos.ng02.payload.response.MessageResponse("Ticket não encontrado para upload."));
        } catch (Exception e) {
            System.err.println("[SUPREME-ERROR] Erro no upload de foto para ticket ID " + id + ": " + e.getMessage());
            return ResponseEntity.internalServerError().body(new com.portalcursos.ng02.payload.response.MessageResponse("Erro ao enviar foto."));
        }
    }

    @PutMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('STAFF', 'ADMIN')")
    public ResponseEntity<?> updateStatus(@PathVariable @NonNull Long id, @RequestParam("status") String status) {
        try {
            return repairRepository.findById(id).map(ticket -> {
                ticket.setStatus(RepairTicket.ERepairStatus.valueOf(status.toUpperCase()));
                
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
            }).orElse(ResponseEntity.status(404).body(new com.portalcursos.ng02.payload.response.MessageResponse("Ticket não encontrado para atualização.")));
        } catch (Exception e) {
            System.err.println("[SUPREME-ERROR] Erro ao atualizar status ticket ID " + id + ": " + e.getMessage());
            return ResponseEntity.internalServerError().body(new com.portalcursos.ng02.payload.response.MessageResponse("Erro ao atualizar status."));
        }
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('STAFF', 'ADMIN')")
    public ResponseEntity<?> deleteTicket(@PathVariable @NonNull Long id) {
        try {
            return repairRepository.findById(id).map(ticket -> {
                repairRepository.delete(ticket);
                return ResponseEntity.ok(new com.portalcursos.ng02.payload.response.MessageResponse("Ticket removido com sucesso."));
            }).orElse(ResponseEntity.status(404).body(new com.portalcursos.ng02.payload.response.MessageResponse("Ticket não encontrado para remoção.")));
        } catch (Exception e) {
            System.err.println("[SUPREME-ERROR] Erro ao deletar ticket ID " + id + ": " + e.getMessage());
            return ResponseEntity.internalServerError().body(new com.portalcursos.ng02.payload.response.MessageResponse("Erro ao remover ticket."));
        }
    }
}
