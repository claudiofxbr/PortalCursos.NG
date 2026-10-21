package com.portalcursos.ng02.service;

import com.portalcursos.ng02.dto.ChargeCatalogDtos.CatalogItemDto;
import com.portalcursos.ng02.dto.ChargeCatalogDtos.ValueSource;
import com.portalcursos.ng02.exception.BusinessException;
import com.portalcursos.ng02.model.ChargePrice;
import com.portalcursos.ng02.model.Course;
import com.portalcursos.ng02.model.EAcademicLevel;
import com.portalcursos.ng02.model.EPaymentCategory;
import com.portalcursos.ng02.model.ESecretaryProcessType;
import com.portalcursos.ng02.model.PostgradStudent;
import com.portalcursos.ng02.model.Student;
import com.portalcursos.ng02.repository.ChargePriceRepository;
import com.portalcursos.ng02.repository.CourseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Liga o procedimento escolhido na hora de gerar a cobrança ao valor da tabela de valores
 * ({@code charge_prices}). Regra única de resolução: linha do curso do aluno, depois linha global;
 * se a linha escolhida não tem valor, a cobrança não é lançada.
 */
@Service
@RequiredArgsConstructor
public class ChargeCatalogService {

    /** Resultado validado: a linha efetivamente usada, o valor e os campos derivados do procedimento. */
    public record Resolved(ChargePrice price, BigDecimal value, ValueSource source,
                           EPaymentCategory category, ESecretaryProcessType processType) {
    }

    private final ChargePriceRepository priceRepository;
    private final CourseRepository courseRepository;

    /** Procedimentos aplicáveis ao aluno (nível e curso), já com o valor que seria cobrado. */
    @Transactional(readOnly = true)
    public List<CatalogItemDto> catalog(Student student, EAcademicLevel level) {
        UUID courseId = courseIdOf(student);
        Map<String, ChargePrice> byCode = new LinkedHashMap<>();
        for (ChargePrice row : priceRepository.findAllActive()) {
            if (!scopeMatches(row, level) || !courseMatches(row, courseId)) {
                continue;
            }
            // A linha do curso do aluno vence a global do mesmo código.
            ChargePrice current = byCode.get(row.getCode());
            if (current == null || (current.getCourseId() == null && row.getCourseId() != null)) {
                byCode.put(row.getCode(), row);
            }
        }
        return byCode.values().stream().map(ChargeCatalogService::toItem).toList();
    }

    /** Valida o procedimento escolhido para este aluno e devolve o valor que o servidor deve cobrar. */
    @Transactional(readOnly = true)
    public Resolved resolve(Long chargePriceId, Student student, EAcademicLevel level) {
        ChargePrice chosen = priceRepository.findActiveById(chargePriceId)
                .orElseThrow(() -> new BusinessException("Procedimento não encontrado ou inativo na tabela de valores"));
        if (!scopeMatches(chosen, level)) {
            throw new BusinessException("O procedimento \"" + chosen.getName() + "\" não se aplica ao nível acadêmico do aluno");
        }
        UUID courseId = courseIdOf(student);
        if (!courseMatches(chosen, courseId)) {
            throw new BusinessException("O valor escolhido pertence a outro curso, diferente do curso do aluno");
        }
        ChargePrice effective = chosen;
        if (chosen.getCourseId() == null && courseId != null) {
            effective = priceRepository.findByCodeAndCourseIdAndActiveTrue(chosen.getCode(), courseId).orElse(chosen);
        }
        if (effective.getCurrentValue() == null) {
            throw new BusinessException("Valor ainda não definido na tabela de valores para \"" + effective.getName() + "\"");
        }
        return new Resolved(effective, effective.getCurrentValue(), sourceOf(effective),
                categoryOf(effective.getCode()), processTypeOf(effective.getCode()));
    }

    /** Curso do aluno: o vínculo direto; na pós-graduação, na falta dele, o curso desejado pelo nome. */
    public UUID courseIdOf(Student student) {
        if (student == null) {
            return null;
        }
        if (student.getCourse() != null) {
            return student.getCourse().getId();
        }
        if (student instanceof PostgradStudent postgrad && postgrad.getDesiredCourse() != null) {
            return courseRepository.findByDenominacaoCurso(postgrad.getDesiredCourse()).map(Course::getId).orElse(null);
        }
        return null;
    }

    private static boolean scopeMatches(ChargePrice row, EAcademicLevel level) {
        return switch (row.getAcademicScope()) {
            case ALL -> true;
            case GRADUATION -> level == EAcademicLevel.GRADUATION;
            case POSTGRAD -> level == EAcademicLevel.POSTGRADUATE;
        };
    }

    /** Linha global serve a todos; linha de curso só ao aluno daquele curso. */
    private static boolean courseMatches(ChargePrice row, UUID studentCourseId) {
        return row.getCourseId() == null || row.getCourseId().equals(studentCourseId);
    }

    private static ValueSource sourceOf(ChargePrice row) {
        return row.getCourseId() == null ? ValueSource.GLOBAL : ValueSource.COURSE;
    }

    private static CatalogItemDto toItem(ChargePrice row) {
        return new CatalogItemDto(row.getId(), row.getCode(), row.getName(), row.getCategory(),
                categoryOf(row.getCode()), processTypeOf(row.getCode()), row.getCurrentValue(), sourceOf(row),
                row.getCurrentValue() != null, row.getName());
    }

    /** Cobranças recorrentes têm o código igual à categoria; os procedimentos da secretaria são processo interno. */
    static EPaymentCategory categoryOf(String code) {
        try {
            return EPaymentCategory.valueOf(code);
        } catch (IllegalArgumentException ex) {
            return EPaymentCategory.INTERNAL_PROCESS;
        }
    }

    /** Só mapeia o que tem equivalente exato no enum; o resto fica como "outros processos". */
    static ESecretaryProcessType processTypeOf(String code) {
        return switch (code) {
            case "TRANCAMENTO_CANCELAMENTO" -> ESecretaryProcessType.ENROLLMENT_CANCELLATION;
            case "NADA_CONSTA" -> ESecretaryProcessType.CERTIFICATE;
            case "TUITION", "ENROLLMENT_FEE", "DOCUMENT_REQUEST", "LAB_FEE" -> null;
            default -> ESecretaryProcessType.OTHER;
        };
    }
}
