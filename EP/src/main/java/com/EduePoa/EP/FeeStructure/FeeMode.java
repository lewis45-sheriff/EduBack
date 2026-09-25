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
     * <p>
     * WEEKLY_BOARDING decision: weekly boarding is intentionally billed using the
     * BOARDING fee structure. A grade has at most one structure per {@link FeeMode}
     * per year (DAY, BOARDING), and introducing a distinct WEEKLY_BOARDING mode
     * would require configuring an additional fee structure for every grade and
     * tenant. Since the established behaviour treats weekly boarders as boarding
     * students for fee purposes, that mapping is preserved here. If weekly boarding
     * ever needs its own pricing, add a WEEKLY_BOARDING constant and a matching fee
     * structure, then update this mapping, the repository queries and seed data.
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
