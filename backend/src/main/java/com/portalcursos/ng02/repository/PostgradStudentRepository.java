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

    // Verificações Globais (Ignoram o filtro @Where active=true)
    @org.springframework.data.jpa.repository.Query(value = "SELECT COUNT(*) > 0 FROM postgrad_students WHERE email = :email", nativeQuery = true)
    boolean existsByEmailGlobal(@org.springframework.data.repository.query.Param("email") String email);

    @org.springframework.data.jpa.repository.Query(value = "SELECT COUNT(*) > 0 FROM postgrad_students WHERE cpf = :cpf", nativeQuery = true)
    boolean existsByCpfGlobal(@org.springframework.data.repository.query.Param("cpf") String cpf);
}
