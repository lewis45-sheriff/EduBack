package com.EduePoa.EP.FeeStructure.Responses;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

/**
 * A single fee component (line item) that belongs to a fee structure, exposing
 * the per-term figure entered when the structure was built. This drives the
 * optional-fee assignment UI: the frontend picks a line item and the assign
 * endpoint snapshots its {@code amount}.
 * <p>
 * {@code configId} is the id of the fee-structure line item
 * ({@code FeeComponentConfig}) — this is the id the optional-fee assign endpoint
 * expects as {@code feeComponentConfigId}. {@code optional} indicates whether the
 * item may be assigned as an optional fee; {@code parentAssignable} whether a
 * parent may self-assign it.
 */
@Data
@Builder
public class FeeStructureComponentDTO {
    private Long configId;
    private String name;
    private BigDecimal amount;
    private String term;
    private boolean optional;
    private boolean parentAssignable;
}
