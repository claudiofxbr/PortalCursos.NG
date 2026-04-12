package com.portalcursos.ng02.repository;

import com.portalcursos.ng02.model.Course;
import com.portalcursos.ng02.model.ECourseType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface CourseRepository extends JpaRepository<Course, UUID> {
    List<Course> findByType(ECourseType type);
}
