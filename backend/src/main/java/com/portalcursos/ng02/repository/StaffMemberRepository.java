package com.portalcursos.ng02.repository;

import com.portalcursos.ng02.model.StaffMember;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface StaffMemberRepository extends JpaRepository<StaffMember, Long> {

    /**
     * Update em lote (sem carregar a entidade no contexto de persistência): usado por
     * UserService.deleteUser, que remove o User (mesma PK, via @MapsId) na mesma transação.
     * Se a entidade StaffMember ficasse anexada ao contexto, o Hibernate lançaria
     * TransientPropertyValueException ao tentar resolver StaffMember.user no flush seguinte
     * ao remove() do User (achado real de produção, 2026-09-13) — o bulk update evita isso
     * por nunca materializar a entidade gerenciada.
     */
    @Modifying
    @Query("UPDATE StaffMember s SET s.active = false WHERE s.id = :id AND s.active = true")
    int deactivateById(@Param("id") Long id);
    /** Pares (id do colaborador, role) dos colaboradores ativos; evita carregar User.roles (LAZY) fora de transação. */
    @Query("SELECT s.id, r.name FROM StaffMember s JOIN s.user u JOIN u.roles r WHERE s.active = true")
    java.util.List<Object[]> findActiveStaffRoles();

    @Query("SELECT s FROM StaffMember s LEFT JOIN FETCH s.creator WHERE s.active = true")
    java.util.List<StaffMember> findAllByActiveTrue();

    @Query("SELECT s FROM StaffMember s LEFT JOIN FETCH s.creator WHERE s.id = :id AND s.active = true")
    java.util.Optional<StaffMember> findByIdAndActiveTrue(@Param("id") Long id);

    /**
     * Ignora o filtro global {@code @SQLRestriction("active = true")}: a reativação de colaborador
     * precisa reusar a linha inativa existente (PK = id do user) em vez de tentar novo INSERT.
     */
    @Query(value = "SELECT * FROM staff_members WHERE id = :id", nativeQuery = true)
    java.util.Optional<StaffMember> findByIdIncludingInactive(@Param("id") Long id);
}
