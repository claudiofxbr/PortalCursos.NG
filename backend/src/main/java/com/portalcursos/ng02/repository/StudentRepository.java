package com.portalcursos.ng02.repository;

import com.portalcursos.ng02.model.Student;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface StudentRepository extends JpaRepository<Student, Long> {
    Optional<Student> findByRegistrationNumber(String registrationNumber);
    Optional<Student> findByEmail(String email);
    Optional<Student> findByCpf(String cpf);

    @org.springframework.data.jpa.repository.Query(value = "SELECT COUNT(*) > 0 FROM students WHERE email = :email", nativeQuery = true)
    boolean existsByEmailGlobal(@org.springframework.data.repository.query.Param("email") String email);

    @org.springframework.data.jpa.repository.Query(value = "SELECT COUNT(*) > 0 FROM students WHERE cpf = :cpf", nativeQuery = true)
    boolean existsByCpfGlobal(@org.springframework.data.repository.query.Param("cpf") String cpf);
}
