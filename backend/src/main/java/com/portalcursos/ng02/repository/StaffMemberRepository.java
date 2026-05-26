package com.portalcursos.ng02.repository;

import com.portalcursos.ng02.model.StaffMember;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface StaffMemberRepository extends JpaRepository<StaffMember, Long> {
    java.util.List<StaffMember> findAllByActiveTrue();
    java.util.Optional<StaffMember> findByIdAndActiveTrue(Long id);
}
