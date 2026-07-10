package com.portalcursos.ng02.repository;

import com.portalcursos.ng02.model.PostgradStudent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.List;

@Repository
public interface PostgradStudentRepository extends JpaRepository<PostgradStudent, Long> {
    
    @org.springframework.data.jpa.repository.Query("SELECT p FROM PostgradStudent p WHERE p.email = :email AND p.active = true")
    Optional<PostgradStudent> findByEmailAndActiveTrue(@org.springframework.data.repository.query.Param("email") String email);

    @org.springframework.data.jpa.repository.Query("SELECT p FROM PostgradStudent p WHERE p.cpf = :cpf AND p.active = true")
    Optional<PostgradStudent> findByCpfAndActiveTrue(@org.springframework.data.repository.query.Param("cpf") String cpf);

    @org.springframework.data.jpa.repository.Query("SELECT p FROM PostgradStudent p WHERE p.active = true")
    List<PostgradStudent> findAllActive();

    @org.springframework.data.jpa.repository.Query("SELECT p FROM PostgradStudent p WHERE p.id = :id AND p.active = true")
    Optional<PostgradStudent> findByIdAndActiveTrue(@org.springframework.data.repository.query.Param("id") Long id);

    Optional<PostgradStudent> findByUserId(Long userId);

    // Verificações Globais (Apenas se ativos)
    @org.springframework.data.jpa.repository.Query(value = "SELECT COUNT(*) > 0 FROM students WHERE email = :email AND active = true", nativeQuery = true)
    boolean existsByEmailGlobal(@org.springframework.data.repository.query.Param("email") String email);

    @org.springframework.data.jpa.repository.Query(value = "SELECT COUNT(*) > 0 FROM students WHERE cpf = :cpf AND active = true", nativeQuery = true)
    boolean existsByCpfGlobal(@org.springframework.data.repository.query.Param("cpf") String cpf);

}
