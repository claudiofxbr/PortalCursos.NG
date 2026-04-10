package com.portalcursos.ng02.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "roles")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Role {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(length = 20, unique = true)
    private ERole name;

    public enum ERole {
        ROLE_ROOT_MASTER,        // Infraestrutura de TI e Administração Suprema (Nível 1)
        ROLE_ADMIN,              // Reitoria / Diretoria Geral (Nível 2)
        ROLE_SECRETARIA,         // Fluxo Acadêmico Institucional (Nível 3)
        ROLE_FINANCEIRO,         // Gestão Financeira e Pagamentos (Nível 4)
        ROLE_ACADEMICO,          // Registro de Notas e Frequência (Nível 5)
        ROLE_MATRICULA,          // Gestão de Vendas e Captação (Nível 6)
        ROLE_COORDENADOR,        // Planejamento de Cursos (Nível 7)
        ROLE_PROFESSOR,          // Controle Pedagógico (Nível 8)
        ROLE_MONITOR,            // Apoio Pedagógico (Nível 9)
        ROLE_BIBLIOTECARIO,      // Gestão de Acervo (Nível 10)
        ROLE_ALUNO,              // Perfil de Estudante
        ROLE_STUDENT,            // Perfil de Estudante (Legacy)
        ROLE_CANDIDATO           // Perfil de Pré-Matrícula
    }
}
