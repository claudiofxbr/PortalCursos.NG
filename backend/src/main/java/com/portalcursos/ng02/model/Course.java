package com.portalcursos.ng02.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.time.LocalDate;
import java.util.UUID;

import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.Where;

@Entity
@Table(name = "courses")
@Data
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@EqualsAndHashCode(callSuper=true)
@SQLDelete(sql = "UPDATE courses SET active = false WHERE id = ?")
@Where(clause = "active = true")
public class Course extends BaseAuditEntity {

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
    @Builder.Default
    private boolean isLocked = false;
}
