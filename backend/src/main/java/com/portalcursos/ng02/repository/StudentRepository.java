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
    Optional<Student> findByUserId(Long userId);
    Optional<Student> findByIdAndActiveTrue(Long id);

    @org.springframework.data.jpa.repository.Query("SELECT DISTINCT s FROM Student s LEFT JOIN FETCH s.course LEFT JOIN FETCH s.creator LEFT JOIN FETCH s.documents WHERE s.active = true AND TYPE(s) = Student")
    java.util.List<Student> findAllWithCourseAndCreator();

    @org.springframework.data.jpa.repository.Query("SELECT DISTINCT s FROM Student s LEFT JOIN FETCH s.course WHERE s.active = true AND TYPE(s) = Student")
    java.util.List<Student> findAllWithCourse();

    @org.springframework.data.jpa.repository.Query("SELECT s FROM Student s LEFT JOIN FETCH s.course LEFT JOIN FETCH s.documents WHERE s.id = :id AND s.active = true")
    Optional<Student> findByIdWithDocuments(@org.springframework.data.repository.query.Param("id") Long id);

    @org.springframework.data.jpa.repository.Query(value = "SELECT COUNT(*) > 0 FROM students WHERE email = :email AND active = true", nativeQuery = true)
    boolean existsByEmailGlobal(@org.springframework.data.repository.query.Param("email") String email);

    @org.springframework.data.jpa.repository.Query(value = "SELECT COUNT(*) > 0 FROM students WHERE cpf = :cpf AND active = true", nativeQuery = true)
    boolean existsByCpfGlobal(@org.springframework.data.repository.query.Param("cpf") String cpf);
}
