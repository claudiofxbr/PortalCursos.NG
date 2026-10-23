package com.portalcursos.ng02.controller;

import com.portalcursos.ng02.dto.ChargeCatalogDtos.CatalogItemDto;
import com.portalcursos.ng02.dto.StudentSearchHit;
import com.portalcursos.ng02.service.FinanceStudentSearchService;
import com.portalcursos.ng02.exception.BusinessException;
import com.portalcursos.ng02.exception.ResourceNotFoundException;
import com.portalcursos.ng02.model.EAcademicLevel;
import com.portalcursos.ng02.model.Student;
import com.portalcursos.ng02.repository.PostgradStudentRepository;
import com.portalcursos.ng02.repository.StudentRepository;
import com.portalcursos.ng02.service.ChargeCatalogService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Procedimentos cobráveis do aluno, com o valor vigente, para preencher o formulário de nova cobrança.
 * Fica separado de {@code /api/finance/prices}: aqui só sai o valor vigente (sem histórico nem propostas) e
 * quem lança cobrança (inclusive o financeiro) pode consultar.
 */
@RestController
@RequestMapping("/api/finance")
@RequiredArgsConstructor
public class ChargeCatalogController {

    private final ChargeCatalogService catalogService;
    private final FinanceStudentSearchService studentSearchService;
    private final StudentRepository studentRepository;
    private final PostgradStudentRepository postgradStudentRepository;

    @GetMapping("/charge-catalog")
    @PreAuthorize("hasAnyRole('ADMIN', 'SECRETARIA', 'FINANCEIRO', 'ROOT_MASTER')")
    public ResponseEntity<List<CatalogItemDto>> catalog(@RequestParam Long studentId, @RequestParam String level) {
        EAcademicLevel academicLevel = parseLevel(level);
        Student student = academicLevel == EAcademicLevel.GRADUATION
                ? studentRepository.findById(studentId)
                        .orElseThrow(() -> new ResourceNotFoundException("Estudante de graduação não encontrado"))
                : postgradStudentRepository.findById(studentId)
                        .orElseThrow(() -> new ResourceNotFoundException("Estudante de pós-graduação não encontrado"));
        return ResponseEntity.ok(catalogService.catalog(student, academicLevel));
    }

    /** Busca de aluno por nome ou CPF (mín. 3 caracteres, até 10 resultados, CPF mascarado) para o formulário de cobrança. */
    @GetMapping("/students/search")
    @PreAuthorize("hasAnyRole('ADMIN', 'SECRETARIA', 'FINANCEIRO', 'ROOT_MASTER')")
    public ResponseEntity<List<StudentSearchHit>> searchStudents(@RequestParam String q, @RequestParam String level) {
        return ResponseEntity.ok(studentSearchService.search(q, parseLevel(level)));
    }

    /**
     * Identificação mínima do aluno escolhido (nome, CPF mascarado, matrícula). Existe para o financeiro, que lança cobrança
     * mas não tem acesso ao cadastro completo do aluno.
     */
    @GetMapping("/students/{id}/summary")
    @PreAuthorize("hasAnyRole('ADMIN', 'SECRETARIA', 'FINANCEIRO', 'ROOT_MASTER')")
    public ResponseEntity<StudentSearchHit> studentSummary(@PathVariable Long id, @RequestParam String level) {
        return ResponseEntity.ok(studentSearchService.summary(id, parseLevel(level)));
    }

    private static EAcademicLevel parseLevel(String level) {
        try {
            return EAcademicLevel.valueOf(level.toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new BusinessException("Nível acadêmico inválido: " + level);
        }
    }
}
