package com.portalcursos.ng02.repository;

import com.portalcursos.ng02.model.ChargePriceChange;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ChargePriceChangeRepository extends JpaRepository<ChargePriceChange, Long> {

    Optional<ChargePriceChange> findFirstByChargePriceIdAndStatus(Long chargePriceId, ChargePriceChange.Status status);

    List<ChargePriceChange> findByChargePriceIdInAndStatus(Collection<Long> chargePriceIds, ChargePriceChange.Status status);

    List<ChargePriceChange> findByChargePriceIdOrderByProposedAtDesc(Long chargePriceId);

    /** Trava a proposta para duas decisões simultâneas não a aplicarem duas vezes. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from ChargePriceChange c where c.id = :id")
    Optional<ChargePriceChange> lockById(@Param("id") Long id);
}
