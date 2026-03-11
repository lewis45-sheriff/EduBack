package com.EduePoa.EP.academics.service;


import com.EduePoa.EP.Utils.CustomResponse;
import com.EduePoa.EP.academics.dto.request.UploadMarksRequest;
import com.EduePoa.EP.academics.dto.response.UploadMarksResponseDto;

import java.util.List;

public interface UploadMarksService {
    CustomResponse<UploadMarksResponseDto> uploadMarks(List<UploadMarksRequest> requests);
}
