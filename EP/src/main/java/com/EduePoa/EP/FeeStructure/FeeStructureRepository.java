package com.EduePoa.EP.FeeStructure;

import com.EduePoa.EP.Grade.Grade;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface FeeStructureRepository extends JpaRepository< FeeStructure,Long > {
    Optional<FeeStructure> findByGradeAndYear(Grade grade, Integer year);
     List<FeeStructure> findByIsDeletedAndDeletedOrderByDatePostedDesc(char isDeleted, char deleted);


}
