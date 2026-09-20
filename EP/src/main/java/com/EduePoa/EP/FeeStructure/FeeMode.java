package com.EduePoa.EP.FeeStructure;

import com.EduePoa.EP.StudentRegistration.BoardingStatus;

/**
 * The mode a fee structure applies to. A grade can have one fee structure per
 * mode (per year), e.g. a DAY structure and a BOARDING structure.
 */
public enum FeeMode {
    DAY,
    BOARDING;

    /**
     * Maps a student's {@link BoardingStatus} to the fee mode that should be
     * charged. Day scholars use DAY; boarding and weekly-boarding students use
     * BOARDING. Defaults to DAY when the status is not set.
     */
    public static FeeMode fromBoardingStatus(BoardingStatus boardingStatus) {
        if (boardingStatus == null) {
            return DAY;
        }
        return switch (boardingStatus) {
            case BOARDING, WEEKLY_BOARDING -> BOARDING;
            case DAY -> DAY;
        };
    }
}
