package com.EduePoa.EP.Transport.Boarding.internal;

/**
 * Internal carrier for a single event's processing result, produced by the per-event transactional
 * processor and mapped to the public per-event outcome DTO by the service.
 */
public record EventOutcome(
        OutcomeType type,
        String clientEventId,
        Long eventId,
        Long studentId,
        String reason) {

    public static EventOutcome accepted(String clientEventId, Long eventId, Long studentId) {
        return new EventOutcome(OutcomeType.ACCEPTED, clientEventId, eventId, studentId, null);
    }

    public static EventOutcome duplicate(String clientEventId, Long eventId, Long studentId) {
        return new EventOutcome(OutcomeType.DUPLICATE, clientEventId, eventId, studentId, null);
    }

    public static EventOutcome rejected(String clientEventId, String reason) {
        return new EventOutcome(OutcomeType.REJECTED, clientEventId, null, null, reason);
    }
}
