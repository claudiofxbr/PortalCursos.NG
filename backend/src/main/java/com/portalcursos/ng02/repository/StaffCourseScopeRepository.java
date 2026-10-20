package com.portalcursos.ng02.repository;

import com.portalcursos.ng02.model.StaffCourseScope;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StaffCourseScopeRepository extends JpaRepository<StaffCourseScope, Long> {

    List<StaffCourseScope> findByStaffId(Long staffId);

    Optional<StaffCourseScope> findByStaffIdAndCourseId(Long staffId, UUID courseId);
}
