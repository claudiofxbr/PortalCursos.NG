package com.portalcursos.ng02.repository;

import com.portalcursos.ng02.model.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, Long> {
    List<Payment> findByStudentId(Long studentId);
    List<Payment> findByPostgradStudentId(Long postgradStudentId);
    List<Payment> findByAcademicLevel(Payment.EAcademicLevel academicLevel);
    List<Payment> findByStatusIn(java.util.List<Payment.EPaymentStatus> statuses);
    List<Payment> findByStatus(Payment.EPaymentStatus status);
    List<Payment> findByAcademicLevelAndStatusIn(Payment.EAcademicLevel level, java.util.List<Payment.EPaymentStatus> statuses);
    List<Payment> findByAcademicLevelAndStatus(Payment.EAcademicLevel level, Payment.EPaymentStatus status);
    List<Payment> findByAcademicLevelAndCategoryAndStatusIn(Payment.EAcademicLevel level, Payment.EPaymentCategory category, java.util.List<Payment.EPaymentStatus> statuses);
}
