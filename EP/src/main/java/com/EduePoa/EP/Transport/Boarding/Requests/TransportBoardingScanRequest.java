package com.EduePoa.EP.Transport.Boarding.Requests;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * A batch of one or more boarding events. Safe to resubmit repeatedly (offline sync); duplicate
 * events are reported as duplicates rather than creating new attendance rows.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransportBoardingScanRequest {

    @NotEmpty(message = "events must not be empty")
    @Valid
    private List<BoardingEventRequest> events;
}
