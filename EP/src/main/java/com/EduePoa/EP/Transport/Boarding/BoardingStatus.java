package com.EduePoa.EP.Transport.Boarding;

/**
 * Status of a recorded transport boarding event.
 *
 * <p>Newly recorded events always use {@link #ON_TIME}. Automatic late calculation is intentionally
 * not implemented because no authoritative route schedule / stop timetable has been specified.
 * {@link #LATE} is reserved for future scheduling support; {@link #MANUAL_OVERRIDE} is reserved for a
 * future audited correction workflow and is never assigned automatically by the scan endpoint.
 *
 * <p>Named distinctly from {@code com.EduePoa.EP.StudentRegistration.BoardingStatus} (hostel/day-scholar
 * boarding) which is an unrelated concept.
 */
public enum BoardingStatus {
    ON_TIME,
    LATE,
    MANUAL_OVERRIDE
}
