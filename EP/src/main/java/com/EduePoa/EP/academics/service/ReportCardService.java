package com.EduePoa.EP.academics.service;

import com.EduePoa.EP.Utils.CustomResponse;
import com.EduePoa.EP.academics.dto.response.ReportCardDto;

public interface ReportCardService {

    /**
     * Assemble a learner's report card for a term/year: learning-area results, competencies, values,
     * attendance, comments and (only if a ranking policy is enabled) position.
     */
    CustomResponse<ReportCardDto> getReportCard(Long studentId, Long termId, int year);
}
