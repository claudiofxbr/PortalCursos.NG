package com.portalcursos.ng02.repository;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CourseRepository extends JpaRepository<Course, UUID> {
    
    @Query("SELECT c FROM Course c LEFT JOIN FETCH c.creator WHERE c.active = true")
    List<Course> findAllActiveWithCreator();

    @Query("SELECT c FROM Course c LEFT JOIN FETCH c.creator WHERE c.id = :id AND c.active = true")
    Optional<Course> findByIdActiveWithCreator(@Param("id") UUID id);
}

