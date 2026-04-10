package com.portalcursos.ng02.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "student_documents")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StudentDocument {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    private EDocumentType documentType;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private EDocumentStatus status = EDocumentStatus.PENDING;

    private String filePath;
    private String rejectionReason;

    @Builder.Default
    private LocalDateTime uploadDate = LocalDateTime.now();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id")
    private Student student;
}
