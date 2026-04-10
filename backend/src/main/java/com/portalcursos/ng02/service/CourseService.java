package com.portalcursos.ng02.service;

import com.portalcursos.ng02.model.Course;
import com.portalcursos.ng02.repository.CourseRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class CourseService {

    @Autowired
    private CourseRepository courseRepository;

    public List<Course> getAllCourses() {
        return courseRepository.findAll();
    }

    public Course getCourseById(UUID id) {
        return courseRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Curso não encontrado com id: " + id));
    }

    @Transactional
    public Course createCourse(Course course) {
        validateMECRules(course);
        
        // Garantir que novos campos sejam sincronizados com os legados se necessário
        if (course.getName() == null) course.setName(course.getDenominacaoCurso());
        if (course.getCoordinatorName() == null) course.setCoordinatorName("Coordenador: " + course.getCpfCoordenador());
        
        // Após a criação, o curso é bloqueado para edição de campos críticos conforme solicitado
        course.setLocked(true);
        
        return courseRepository.save(course);
    }

    @Transactional
    public Course updateCourse(UUID id, Course details) {
        Course course = getCourseById(id);

        if (course.isLocked()) {
            // Se bloqueado, apenas alguns campos não críticos podem ser alterados
            course.setDescription(details.getDescription());
            course.setTotalVacancies(details.getTotalVacancies());
            course.setMonthlyFee(details.getMonthlyFee());
            course.setTitulacaoCoordenador(details.getTitulacaoCoordenador());
            course.setPercentualDocentesStrictoSensu(details.getPercentualDocentesStrictoSensu());
        } else {
            // Se por algum motivo não estiver bloqueado, permite edição completa
            // (Isso seria raro no novo fluxo)
            updateFullCourse(course, details);
        }

        return courseRepository.save(course);
    }

    private void updateFullCourse(Course course, Course details) {
        course.setCodigoIes(details.getCodigoIes());
        course.setDenominacaoCurso(details.getDenominacaoCurso());
        course.setName(details.getDenominacaoCurso());
        course.setNivelPosGraduacao(details.getNivelPosGraduacao());
        course.setModalidade(details.getModalidade());
        course.setAreaConhecimento(details.getAreaConhecimento());
        course.setCargaHorariaTotal(details.getCargaHorariaTotal());
        course.setNumeroDocumentoCriacao(details.getNumeroDocumentoCriacao());
        course.setDataDocumentoCriacao(details.getDataDocumentoCriacao());
        course.setDataInicioOferta(details.getDataInicioOferta());
        course.setCpfCoordenador(details.getCpfCoordenador());
        course.setTitulacaoCoordenador(details.getTitulacaoCoordenador());
        course.setPercentualDocentesStrictoSensu(details.getPercentualDocentesStrictoSensu());
    }

    private void validateMECRules(Course course) {
        // 1. Validação de Carga Horária (MEC 2026 - Lato Sensu)
        if (course.getNivelPosGraduacao() == Course.ENivelPosGraduacao.LATO_SENSU) {
            if (course.getCargaHorariaTotal() == null || course.getCargaHorariaTotal() < 360) {
                throw new RuntimeException("Regra MEC 2026: Cursos Lato Sensu devem ter no mínimo 360 horas.");
            }
        }

        // 2. Validação de Modalidade (EaD)
        if (course.getModalidade() == Course.EModalidade.EAD) {
            // Simulação de verificação de credenciamento da IES no e-MEC
            boolean hasEADAccreditation = true; // Em um cenário real, consultaríamos um config ou API
            if (!hasEADAccreditation) {
                throw new RuntimeException("A instituição não possui ato de credenciamento para EaD ativo no e-MEC.");
            }
        }

        // 3. Validação Stricto Sensu (CAPES / Sucupira)
        if (course.getNivelPosGraduacao() == Course.ENivelPosGraduacao.STRICTO_SENSU) {
            if (course.getNumeroDocumentoCriacao() == null || course.getNumeroDocumentoCriacao().isEmpty()) {
                throw new RuntimeException("Cursos Stricto Sensu exigem aprovação prévia na CAPES (Plataforma Sucupira).");
            }
        }
    }

    public void deleteCourse(UUID id) {
        Course course = getCourseById(id);
        courseRepository.delete(course);
    }
}
