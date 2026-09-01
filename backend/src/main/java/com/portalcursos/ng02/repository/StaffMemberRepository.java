package com.portalcursos.ng02.repository;

import com.portalcursos.ng02.model.StaffMember;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface StaffMemberRepository extends JpaRepository<StaffMember, Long> {
    @Query("SELECT s FROM StaffMember s LEFT JOIN FETCH s.creator WHERE s.active = true")
    java.util.List<StaffMember> findAllByActiveTrue();

    @Query("SELECT s FROM StaffMember s LEFT JOIN FETCH s.creator WHERE s.id = :id AND s.active = true")
    java.util.Optional<StaffMember> findByIdAndActiveTrue(@Param("id") Long id);
}
