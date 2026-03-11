package com.EduePoa.EP.Grade;

import com.EduePoa.EP.Grade.Requests.GradeDto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface GradeRepository extends JpaRepository<Grade,Long> {
    Optional<Grade> findByName( String Name);
}
