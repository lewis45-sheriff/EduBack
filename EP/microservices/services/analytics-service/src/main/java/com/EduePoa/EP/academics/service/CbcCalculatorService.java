package com.EduePoa.EP.academics.service;


import com.EduePoa.EP.Utils.CustomResponse;
import com.EduePoa.EP.academics.dto.response.CbcResultDto;

import java.util.List;

public interface CbcCalculatorService {
    CustomResponse<List<CbcResultDto>> computeForGrade(Long gradeId, Long termId, int year);
    CustomResponse<List<CbcResultDto>> getResultsForStudent(Long studentId, Long termId);
}
