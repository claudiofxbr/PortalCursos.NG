package com.portalcursos.ng02.repository;

import com.portalcursos.ng02.model.RepairTicket;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RepairRepository extends JpaRepository<RepairTicket, Long> {
    List<RepairTicket> findByStatus(RepairTicket.ERepairStatus status);
}
