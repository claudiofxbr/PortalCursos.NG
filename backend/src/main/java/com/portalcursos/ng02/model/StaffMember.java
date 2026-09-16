package com.portalcursos.ng02.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import lombok.experimental.SuperBuilder;
import lombok.EqualsAndHashCode;
import com.fasterxml.jackson.annotation.JsonIgnore;

// @SQLDelete removido (mesmo achado do Payment/Student/RepairTicket): o SQL
// customizado não incluía o parâmetro de @Version que o Hibernate injeta para
// entidades versionadas, então staffRepository.delete()/deleteById() sempre
// lançaria DataIntegrityViolationException. Diferente de Student/RepairTicket,
// aqui não era código morto: StaffMemberController.deleteStaff chamava
// delete() de verdade (endpoint sem consumidor no frontend hoje, mas quebrado
// para quem chamasse via API). Soft-delete real é via setActive(false) + save()
// no controller, mesmo padrão de RepairController.deleteTicket.
@Entity
@Table(name = "staff_members")
@Data
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
// Sem @SQLRestriction: StaffMember é apontado como `creator` por todo registro
// auditado (BaseAuditEntity). Esconder staff inativo globalmente fazia
// getCreatorName() lançar EntityNotFoundException ao serializar registros de um
// colaborador desligado. Listagens de staff filtram active=true explicitamente
// (StaffMemberRepository.findAllByActiveTrue / findByIdAndActiveTrue).
@EqualsAndHashCode(callSuper=true)
public class StaffMember extends BaseAuditEntity {
    @Id
    private Long id;

    @NotBlank
    private String fullName;

    @NotBlank
    private String position;

    @NotBlank
    private String department;

    @Column(name = "foto_url")
    private String fotoUrl;

    @OneToOne
    @MapsId
    @JoinColumn(name = "id")
    @JsonIgnore
    private User user;
}
