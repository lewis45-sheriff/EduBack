package com.EduePoa.EP.academics.assessment.service;

import com.EduePoa.EP.Utils.CustomResponse;
import com.EduePoa.EP.academics.assessment.dto.RecordTeacherRemarkRequest;

/**
 * Write/read the class teacher's per-learner, per-term remark that appears on the CBC report card.
 */
public interface TeacherRemarkService {

    /** Record (or clear, when blank) the class teacher's remark for a learner in a term/year. */
    CustomResponse<?> recordRemark(RecordTeacherRemarkRequest request);

    /** Read the current class teacher's remark for a learner in a term/year. */
    CustomResponse<?> getRemark(Long studentId, Long termId, int year);
}
