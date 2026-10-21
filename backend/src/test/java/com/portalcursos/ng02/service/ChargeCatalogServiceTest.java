package com.portalcursos.ng02.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Resolução do valor pelo procedimento: curso do aluno, depois global; sem valor não lança. */
@ExtendWith(MockitoExtension.class)
class ChargeCatalogServiceTest {

    private static final UUID COURSE_A = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID COURSE_B = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");

    @Mock private ChargePriceRepository priceRepository;
    @Mock private CourseRepository courseRepository;
    @InjectMocks private ChargeCatalogService service;

    private static ChargePrice price(long id, String code, ChargePrice.Scope scope, UUID course, String value) {
        ChargePrice p = new ChargePrice();
        p.setId(id);
        p.setCode(code);
        p.setCategory("Categoria");
        p.setName("Nome " + code);
        p.setAcademicScope(scope);
        p.setCourseId(course);
        p.setCurrentValue(value == null ? null : new BigDecimal(value));
        return p;
    }

    private static Student studentOf(UUID courseId) {
        Course course = new Course();
        course.setId(courseId);
        return Student.builder().id(1L).course(courseId == null ? null : course).build();
    }

    @Test
    void catalogoPreferaALinhaDoCursoDoAlunoSobreAGlobalDoMesmoCodigo() {
        when(priceRepository.findAllActive()).thenReturn(List.of(
                price(1, "REMATRICULA", ChargePrice.Scope.ALL, null, "100.00"),
                price(2, "REMATRICULA", ChargePrice.Scope.ALL, COURSE_A, "120.00"),
                price(3, "REMATRICULA", ChargePrice.Scope.ALL, COURSE_B, "130.00")));

        List<CatalogItemDto> items = service.catalog(studentOf(COURSE_A), EAcademicLevel.GRADUATION);

        assertEquals(1, items.size());
        assertEquals(2L, items.get(0).priceId());
        assertEquals(new BigDecimal("120.00"), items.get(0).value());
        assertEquals(ValueSource.COURSE, items.get(0).valueSource());
    }

    @Test
    void catalogoSemCursoDoAlunoUsaSoLinhasGlobais() {
        when(priceRepository.findAllActive()).thenReturn(List.of(
                price(1, "REMATRICULA", ChargePrice.Scope.ALL, null, "100.00"),
                price(2, "REMATRICULA", ChargePrice.Scope.ALL, COURSE_A, "120.00")));

        List<CatalogItemDto> items = service.catalog(studentOf(null), EAcademicLevel.GRADUATION);

        assertEquals(1, items.size());
        assertEquals(ValueSource.GLOBAL, items.get(0).valueSource());
        assertEquals(new BigDecimal("100.00"), items.get(0).value());
    }

    @Test
    void catalogoFiltraPorNivelAcademico() {
        when(priceRepository.findAllActive()).thenReturn(List.of(
                price(1, "ATIVIDADES_COMPLEMENTARES", ChargePrice.Scope.GRADUATION, null, "10.00"),
                price(2, "EXAME_QUALIFICACAO", ChargePrice.Scope.POSTGRAD, null, "20.00"),
                price(3, "NADA_CONSTA", ChargePrice.Scope.ALL, null, "5.00")));

        assertEquals(List.of("ATIVIDADES_COMPLEMENTARES", "NADA_CONSTA"),
                service.catalog(studentOf(null), EAcademicLevel.GRADUATION).stream().map(CatalogItemDto::code).toList());
        assertEquals(List.of("EXAME_QUALIFICACAO", "NADA_CONSTA"),
                service.catalog(studentOf(null), EAcademicLevel.POSTGRADUATE).stream().map(CatalogItemDto::code).toList());
    }

    @Test
    void catalogoMarcaValorNaoDefinidoSemEsconderOProcedimento() {
        when(priceRepository.findAllActive()).thenReturn(List.of(price(1, "TUITION", ChargePrice.Scope.ALL, null, null)));

        CatalogItemDto item = service.catalog(studentOf(null), EAcademicLevel.GRADUATION).get(0);

        assertFalse(item.valueDefined());
        assertEquals(null, item.value());
    }

    @Test
    void posGraduacaoSemVinculoUsaOCursoDesejadoPeloNome() {
        Course course = new Course();
        course.setId(COURSE_A);
        PostgradStudent postgrad = PostgradStudent.builder().id(2L).desiredCourse("Mestrado X").build();
        when(courseRepository.findByDenominacaoCurso("Mestrado X")).thenReturn(Optional.of(course));
        when(priceRepository.findAllActive()).thenReturn(List.of(
                price(1, "REGISTRO_ORIENTACAO", ChargePrice.Scope.POSTGRAD, null, "50.00"),
                price(2, "REGISTRO_ORIENTACAO", ChargePrice.Scope.POSTGRAD, COURSE_A, "60.00")));

        List<CatalogItemDto> items = service.catalog(postgrad, EAcademicLevel.POSTGRADUATE);

        assertEquals(new BigDecimal("60.00"), items.get(0).value());
    }

    @Test
    void resolveDevolveOValorEOsCamposDerivadosDoProcedimento() {
        when(priceRepository.findActiveById(7L)).thenReturn(Optional.of(
                price(7, "NADA_CONSTA", ChargePrice.Scope.ALL, null, "15.50")));

        ChargeCatalogService.Resolved r = service.resolve(7L, studentOf(null), EAcademicLevel.GRADUATION);

        assertEquals(new BigDecimal("15.50"), r.value());
        assertEquals(EPaymentCategory.INTERNAL_PROCESS, r.category());
        assertEquals(ESecretaryProcessType.CERTIFICATE, r.processType());
    }

    @Test
    void resolveTrocaALinhaGlobalPelaDoCursoDoAluno() {
        when(priceRepository.findActiveById(1L)).thenReturn(Optional.of(
                price(1, "REMATRICULA", ChargePrice.Scope.ALL, null, "100.00")));
        when(priceRepository.findByCodeAndCourseIdAndActiveTrue("REMATRICULA", COURSE_A)).thenReturn(Optional.of(
                price(2, "REMATRICULA", ChargePrice.Scope.ALL, COURSE_A, "120.00")));

        ChargeCatalogService.Resolved r = service.resolve(1L, studentOf(COURSE_A), EAcademicLevel.GRADUATION);

        assertEquals(2L, r.price().getId());
        assertEquals(new BigDecimal("120.00"), r.value());
        assertEquals(ValueSource.COURSE, r.source());
    }

    @Test
    void resolveAceitaValorZeroParaProcedimentoGratuito() {
        when(priceRepository.findActiveById(1L)).thenReturn(Optional.of(
                price(1, "NADA_CONSTA", ChargePrice.Scope.ALL, null, "0.00")));

        assertEquals(0, service.resolve(1L, studentOf(null), EAcademicLevel.GRADUATION).value().signum());
    }

    @Test
    void resolveRecusaLinhaSemValorDefinido() {
        when(priceRepository.findActiveById(1L)).thenReturn(Optional.of(
                price(1, "TUITION", ChargePrice.Scope.ALL, null, null)));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.resolve(1L, studentOf(null), EAcademicLevel.GRADUATION));

        assertTrue(ex.getMessage().contains("Valor ainda não definido"));
    }

    @Test
    void resolveRecusaProcedimentoInexistenteOuInativo() {
        when(priceRepository.findActiveById(99L)).thenReturn(Optional.empty());

        assertThrows(BusinessException.class, () -> service.resolve(99L, studentOf(null), EAcademicLevel.GRADUATION));
    }

    @Test
    void resolveRecusaProcedimentoDeOutroNivel() {
        when(priceRepository.findActiveById(1L)).thenReturn(Optional.of(
                price(1, "EXAME_QUALIFICACAO", ChargePrice.Scope.POSTGRAD, null, "20.00")));

        assertThrows(BusinessException.class, () -> service.resolve(1L, studentOf(null), EAcademicLevel.GRADUATION));
    }

    @Test
    void resolveRecusaLinhaDeOutroCurso() {
        when(priceRepository.findActiveById(3L)).thenReturn(Optional.of(
                price(3, "REMATRICULA", ChargePrice.Scope.ALL, COURSE_B, "130.00")));

        assertThrows(BusinessException.class, () -> service.resolve(3L, studentOf(COURSE_A), EAcademicLevel.GRADUATION));
    }

    @Test
    void cobrancaRecorrenteMantemASuaCategoriaESemTipoDeProcesso() {
        lenient().when(priceRepository.findActiveById(1L)).thenReturn(Optional.of(
                price(1, "TUITION", ChargePrice.Scope.ALL, null, "900.00")));

        ChargeCatalogService.Resolved r = service.resolve(1L, studentOf(null), EAcademicLevel.GRADUATION);

        assertEquals(EPaymentCategory.TUITION, r.category());
        assertEquals(null, r.processType());
    }
}
