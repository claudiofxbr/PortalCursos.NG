package com.portalcursos.ng02.dto;

import com.portalcursos.ng02.model.StudentDocument;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StudentDocumentDTO {
    private Long id;
    private String documentType;
    private String filePath;
    private String status;
    private String rejectionReason;

    public static StudentDocumentDTO from(StudentDocument d) {
        if (d == null) return null;
        return StudentDocumentDTO.builder()
                .id(d.getId())
                .documentType(d.getDocumentType() != null ? d.getDocumentType().name() : null)
                .filePath(d.getFilePath())
                .status(d.getStatus() != null ? d.getStatus().name() : null)
                .rejectionReason(d.getRejectionReason())
                .build();
    }
}
