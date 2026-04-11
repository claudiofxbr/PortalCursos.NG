package com.portalcursos.ng02.dto;

import com.portalcursos.ng02.model.RepairTicket.ERepairStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RepairTicketDTO {
    private Long id;
    private String title;
    private String description;
    private String location;
    private ERepairStatus status;
    private java.util.List<String> photoUrls;
    private String mainPhotoUrl;
    private LocalDateTime createdAt;
    private String reportedByFullName;
    
    // --- Metadados de Auditoria V30.9-SUPREME ---
    private String creatorName;
    private String creatorPosition;
    private String creatorPhotoUrl;
}
