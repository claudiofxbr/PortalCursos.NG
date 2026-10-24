package com.portalcursos.ng02.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.portalcursos.ng02.exception.ResourceNotFoundException;
import com.portalcursos.ng02.model.Course;
import com.portalcursos.ng02.repository.CourseRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.UUID;

/**
 * Regressão (auditoria A2): CourseService.deleteCourse chamava courseRepository.delete(), que
 * dependia de um @SQLDelete sem o parâmetro de @Version (mesmo bug de Payment/RepairTicket/
 * StaffMember) e falhava sempre. Agora é soft-delete explícito (active=false + save).
 */
@SpringBootTest(properties = {
    "SPRING_DATASOURCE_URL=jdbc:h2:mem:coursesoftdeletedb;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
    "SPRING_DATASOURCE_USERNAME=sa",
    "SPRING_DATASOURCE_PASSWORD=",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.flyway.enabled=false",
    "APP_JWT_SECRET=ZXhhbXBsZS1zZWNyZXQta2V5LXdpdGgtZW5vdWdoLWxlbmd0aC1mb3ItYmFzZTY0LWVuY29kaW5nLXByb3Blcmx5",
    "APP_JWT_EXPIRATION=900000",
    "APP_ROOT_PASSWORD=TestRootPass123!",
    "APP_ADMIN_PASSWORD=TestAdminPass123!"
})
class CourseServiceSoftDeleteTest {

    @Autowired
    private CourseService courseService;

    @Autowired
    private CourseRepository courseRepository;

    @Test
    void deleteCourseDesativaCursoEEscondeDasListagens() {
        Course saved = courseRepository.saveAndFlush(Course.builder()
                .codigoIes("IES-DEL")
                .denominacaoCurso("Curso Para Excluir")
                .build());
        UUID id = saved.getId();

        assertDoesNotThrow(() -> courseService.deleteCourse(id));

        assertFalse(courseRepository.findById(id).orElseThrow().isActive(),
                "O curso deve continuar na tabela, só marcado como inativo");
        assertThrows(ResourceNotFoundException.class, () -> courseService.getCourseById(id));
        assertTrue(courseService.getAllCourses().stream().noneMatch(c -> id.equals(c.getId())),
                "Listagem de catálogo não deve mostrar o curso excluído");
    }
}
