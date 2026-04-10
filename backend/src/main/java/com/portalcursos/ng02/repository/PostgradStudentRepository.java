package com.portalcursos.ng02.repository;

import com.portalcursos.ng02.model.PostgradStudent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PostgradStudentRepository extends JpaRepository<PostgradStudent, Long> {
    Optional<PostgradStudent> findByEmail(String email);
    Optional<PostgradStudent> findByCpf(String cpf);
    boolean existsByEmail(String email);
    boolean existsByCpf(String cpf);
}
