package com.EduePoa.EP.Transport.Boarding.Responses;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Aggregate result of a scan batch. Valid events remain persisted regardless of duplicates or
 * rejections elsewhere in the batch.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransportBoardingScanResponse {

    @Builder.Default
    private List<BoardingEventOutcomeResponse> accepted = new ArrayList<>();

    @Builder.Default
    private List<BoardingEventOutcomeResponse> duplicates = new ArrayList<>();

    @Builder.Default
    private List<BoardingEventOutcomeResponse> rejected = new ArrayList<>();

    private int acceptedCount;
    private int duplicateCount;
    private int rejectedCount;
}
