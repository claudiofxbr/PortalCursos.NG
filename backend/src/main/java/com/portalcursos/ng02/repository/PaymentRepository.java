package com.portalcursos.ng02.repository;

import com.portalcursos.ng02.model.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, Long> {
    @Query("SELECT p FROM Payment p LEFT JOIN FETCH p.creator WHERE p.student.id = :studentId")
    List<Payment> findByStudentId(@Param("studentId") Long studentId);

    @Query("SELECT p FROM Payment p LEFT JOIN FETCH p.creator WHERE p.academicLevel = :academicLevel")
    List<Payment> findByAcademicLevel(@Param("academicLevel") EAcademicLevel academicLevel);

    @Query("SELECT p FROM Payment p LEFT JOIN FETCH p.creator WHERE p.status IN :statuses")
    List<Payment> findByStatusIn(@Param("statuses") List<EPaymentStatus> statuses);

    @Query("SELECT p FROM Payment p LEFT JOIN FETCH p.creator WHERE p.status = :status")
    List<Payment> findByStatus(@Param("status") EPaymentStatus status);

    @Query("SELECT p FROM Payment p LEFT JOIN FETCH p.creator WHERE p.academicLevel = :level AND p.status IN :statuses")
    List<Payment> findByAcademicLevelAndStatusIn(@Param("level") EAcademicLevel level, @Param("statuses") List<EPaymentStatus> statuses);

    @Query("SELECT p FROM Payment p LEFT JOIN FETCH p.creator WHERE p.academicLevel = :level AND p.status = :status")
    List<Payment> findByAcademicLevelAndStatus(@Param("level") EAcademicLevel level, @Param("status") EPaymentStatus status);

    @Query("SELECT p FROM Payment p LEFT JOIN FETCH p.creator WHERE p.academicLevel = :level AND p.category = :category AND p.status IN :statuses")
    List<Payment> findByAcademicLevelAndCategoryAndStatusIn(@Param("level") EAcademicLevel level, @Param("category") EPaymentCategory category, @Param("statuses") List<EPaymentStatus> statuses);

    @Query("SELECT p FROM Payment p LEFT JOIN FETCH p.creator LEFT JOIN FETCH p.student WHERE p.id = :id")
    java.util.Optional<Payment> findByIdWithCreatorAndStudent(@Param("id") Long id);
}
