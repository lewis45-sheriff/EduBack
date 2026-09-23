package com.EduePoa.EP.Grade.Stream;

import com.EduePoa.EP.Grade.Stream.Requests.GradeStreamCreateRequest;
import com.EduePoa.EP.Utils.CustomResponse;

public interface GradeStreamService {
    CustomResponse<?> createStreams(GradeStreamCreateRequest request);

    CustomResponse<?> getStreamsByGrade(Long gradeId);

    CustomResponse<?> getAllStreams();

    CustomResponse<?> delete(Long id);

    CustomResponse<?> assignClassTeacher(Long streamId, Long staffId);

    CustomResponse<?> removeClassTeacher(Long streamId);
}
