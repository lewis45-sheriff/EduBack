package com.EduePoa.EP.academics.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UploadMarksResponseDto {
    private int savedCount;
    private int skippedCount;
    private List<String> errors;
}
