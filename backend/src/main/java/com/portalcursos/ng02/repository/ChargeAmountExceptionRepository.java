package com.portalcursos.ng02.repository;

import com.portalcursos.ng02.model.ChargeAmountException;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ChargeAmountExceptionRepository extends JpaRepository<ChargeAmountException, Long> {

    List<ChargeAmountException> findAllByOrderByRequestedAtDesc();

    List<ChargeAmountException> findByRequestedByOrderByRequestedAtDesc(String requestedBy);

    boolean existsByStudentIdAndChargePriceIdAndStatus(Long studentId, Long chargePriceId, ChargeAmountException.Status status);
}
