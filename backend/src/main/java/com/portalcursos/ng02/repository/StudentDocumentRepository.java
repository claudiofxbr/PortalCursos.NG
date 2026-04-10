package com.portalcursos.ng02.repository;

import com.portalcursos.ng02.model.StudentDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface StudentDocumentRepository extends JpaRepository<StudentDocument, Long> {
}
