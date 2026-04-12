package com.portalcursos.ng02.repository;

import com.portalcursos.ng02.model.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, Long> {
    List<Payment> findByStudentId(Long studentId);
    List<Payment> findByPostgradStudentId(Long postgradStudentId);
    List<Payment> findByAcademicLevel(EAcademicLevel academicLevel);
    List<Payment> findByStatusIn(java.util.List<EPaymentStatus> statuses);
    List<Payment> findByStatus(EPaymentStatus status);
    List<Payment> findByAcademicLevelAndStatusIn(EAcademicLevel level, java.util.List<EPaymentStatus> statuses);
    List<Payment> findByAcademicLevelAndStatus(EAcademicLevel level, EPaymentStatus status);
    List<Payment> findByAcademicLevelAndCategoryAndStatusIn(EAcademicLevel level, EPaymentCategory category, java.util.List<EPaymentStatus> statuses);
}
