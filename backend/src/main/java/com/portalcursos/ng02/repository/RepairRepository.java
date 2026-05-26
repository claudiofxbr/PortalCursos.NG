package com.portalcursos.ng02.repository;

import com.portalcursos.ng02.model.RepairTicket;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RepairRepository extends JpaRepository<RepairTicket, Long> {
    
    @org.springframework.data.jpa.repository.Query("SELECT r FROM RepairTicket r WHERE r.status = :status AND r.active = true")
    List<RepairTicket> findByStatusAndActiveTrue(@org.springframework.data.repository.query.Param("status") RepairTicket.ERepairStatus status);

    @org.springframework.data.jpa.repository.Query("SELECT DISTINCT r FROM RepairTicket r LEFT JOIN FETCH r.creator WHERE r.active = true")
    List<RepairTicket> findAllWithCreator();
    
    java.util.Optional<RepairTicket> findByIdAndActiveTrue(Long id);
}
