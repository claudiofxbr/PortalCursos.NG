package com.portalcursos.ng02.repository;

import com.portalcursos.ng02.model.ChargePrice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ChargePriceRepository extends JpaRepository<ChargePrice, Long> {

    @Query("select p from ChargePrice p where p.active = true order by p.category, p.name, p.courseId")
    List<ChargePrice> findAllActive();

    /** Linhas globais mais as dos cursos informados (lista nunca vazia: o chamador garante). */
    @Query("select p from ChargePrice p where p.active = true and (p.courseId is null or p.courseId in :courseIds) "
            + "order by p.category, p.name, p.courseId")
    List<ChargePrice> findVisibleTo(@Param("courseIds") Collection<UUID> courseIds);

    @Query("select p from ChargePrice p where p.id = :id and p.active = true")
    Optional<ChargePrice> findActiveById(@Param("id") Long id);

    boolean existsByCodeAndCourseIdAndActiveTrue(String code, UUID courseId);
}
