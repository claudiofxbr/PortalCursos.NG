package com.portalcursos.ng02.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "courses")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Course {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    // Dados Institucionais e do Curso
    @Column(nullable = false)
    private String codigoIes;

    @Column(nullable = false)
    private String denominacaoCurso;

    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    private ENivelPosGraduacao nivelPosGraduacao;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private EModalidade modalidade;

    private String areaConhecimento;

    private Integer cargaHorariaTotal;

    private UUID cursoGraduacaoVinculadoId;

    // Atos Autorizativos Internos
    private String numeroDocumentoCriacao;
    private LocalDate dataDocumentoCriacao;
    private LocalDate dataInicioOferta;

    // Dados da Coordenação e Corpo Docente
    private String cpfCoordenador;
    private String titulacaoCoordenador;
    private Double percentualDocentesStrictoSensu;

    // Controle de Sistema
    private boolean isLocked = false;

    // Campos legados (mantidos para compatibilidade inicial se necessário)
    private String name;
    @Column(columnDefinition = "TEXT")
    private String description;
    @Enumerated(EnumType.STRING)
    private ECourseType type;
    private Integer durationInSemesters;
    private Integer totalVacancies;
    private String coordinatorName;
    private Double monthlyFee;

    // Auditoria de Emissor (Foto 3x4 e Cargo)
    private String creatorName;
    private String creatorPosition;
    private String creatorPhotoUrl;

    public enum ENivelPosGraduacao {
        LATO_SENSU,
        STRICTO_SENSU,
        GRADUACAO
    }

    public enum EModalidade {
        PRESENCIAL,
        EAD
    }

    public enum ECourseType {
        GRADUATION,
        POSTGRAD
    }
}
