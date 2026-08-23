package com.portalcursos.ng02.repository;

import com.portalcursos.ng02.model.DataDeletionRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DataDeletionRequestRepository extends JpaRepository<DataDeletionRequest, Long> {
    Optional<DataDeletionRequest> findFirstByUserIdAndStatus(Long userId, DataDeletionRequest.Status status);
    List<DataDeletionRequest> findAllByOrderByRequestedAtDesc();
}
