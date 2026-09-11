package com.n0hana.echoes_server.user;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {

    boolean existsByEmail(String email);

    @Query("select a from Admin a where a.active = true")
    Page<Admin> findAdmins(Pageable pageable);

    @Query("select m from Manager m where m.active = true and (:institutionId is null or m.institutionId = :institutionId)")
    Page<Manager> findManagers(@Param("institutionId") UUID institutionId, Pageable pageable);

    @Query("select t from Teacher t where t.active = true and (:institutionId is null or t.institutionId = :institutionId)")
    Page<Teacher> findTeachers(@Param("institutionId") UUID institutionId, Pageable pageable);

    @Query("select s from Student s where s.active = true and (:institutionId is null or s.institutionId = :institutionId)")
    Page<Student> findStudents(@Param("institutionId") UUID institutionId, Pageable pageable);
}
