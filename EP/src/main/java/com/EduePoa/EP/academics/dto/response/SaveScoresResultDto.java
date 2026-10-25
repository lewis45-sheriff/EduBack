package com.EduePoa.EP.academics.dto.response;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * Outcome of a teacher marks upsert: how many score rows were created vs updated, how many blank
 * entries were skipped, and any per-learner validation errors. Idempotent — re-saving the same
 * marks produces 0 created and N updated, never duplicates.
 */
@Data
public class SaveScoresResultDto {
    private int created;
    private int updated;
    private int skippedBlank;
    private String assessmentStatus;
    private List<String> errors = new ArrayList<>();
}
