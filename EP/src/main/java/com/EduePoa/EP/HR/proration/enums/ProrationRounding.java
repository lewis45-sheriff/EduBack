package com.EduePoa.EP.HR.proration.enums;

import java.math.RoundingMode;

/**
 * Rounding applied to prorated money amounts. Maps to a {@link RoundingMode} so the engine
 * stays deterministic and the policy choice is explicit and auditable.
 */
public enum ProrationRounding {
    HALF_UP(RoundingMode.HALF_UP),
    HALF_EVEN(RoundingMode.HALF_EVEN),
    DOWN(RoundingMode.DOWN),
    UP(RoundingMode.UP);

    private final RoundingMode mode;

    ProrationRounding(RoundingMode mode) {
        this.mode = mode;
    }

    public RoundingMode toRoundingMode() {
        return mode;
    }
}
