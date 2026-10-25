package com.EduePoa.EP.academics.service;

import com.EduePoa.EP.Utils.CustomResponse;
import com.EduePoa.EP.academics.dto.response.ReportCardDto;

public interface ReportCardService {

    /**
     * Assemble a learner's report card for a term/year: learning-area results, competencies, values,
     * attendance, comments and (only if a ranking policy is enabled) position.
     */
    CustomResponse<ReportCardDto> getReportCard(Long studentId, Long termId, int year);

    /**
     * List the learners eligible for a report card for the given grade (and optional stream),
     * term and year. Each entry indicates whether results are available ("ready"). Backs the
     * report-card search screen.
     */
    CustomResponse<java.util.List<com.EduePoa.EP.academics.dto.response.ReportCardListItemDto>> listReportCards(
            Long gradeId, Long streamId, Long termId, int year);
}
