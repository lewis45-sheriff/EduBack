package com.EduePoa.EP.Grade;

import com.EduePoa.EP.Utils.CustomResponse;

public interface GradeService {
    CustomResponse<?> createGrade(String name, Integer start, Integer end );
    CustomResponse<?>getAllGrades();
    CustomResponse<?>delete(Long id);

}
