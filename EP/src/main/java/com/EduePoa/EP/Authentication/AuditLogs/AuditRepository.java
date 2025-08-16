package com.EduePoa.EP.Authentication.AuditLogs;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.sql.Date;
import java.util.List;

@Repository
public interface AuditRepository extends JpaRepository<Audit, Long > {
    @Query(value = "SELECT * FROM audit WHERE DATE(timestamp) = :date", nativeQuery = true)
    List<Audit> findByDate(@Param("date") java.util.Date date);



}
