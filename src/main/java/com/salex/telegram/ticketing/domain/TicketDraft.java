package com.salex.telegram.ticketing.domain;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/**
 * Captures partially completed ticket data during multi-step user interaction.
 */
public final class TicketDraft {
    /**
     * Defines the ordered steps required to capture a complete ticket.
     */
    public enum Step {
        SUMMARY,
        PRIORITY,
        DETAILS,
        CONFIRMATION
    }

    private final Map<Step, String> values = new EnumMap<>(Step.class);
    private Long ticketId;

    /**
     * Stores the value supplied for the specified draft step.
     *
     * @param step  the step being completed
     * @param value user-provided value
     * @throws NullPointerException if {@code value} is {@code null}
     */
    public void put(Step step, String value) {
        Objects.requireNonNull(value, "value");
        values.put(step, value);
    }

    /**
     * Retrieves the captured value for the given step.
     *
     * @param step the step to query
     * @return stored value or {@code null} if not yet provided
     */
    public String get(Step step) {
        return values.get(step);
    }

    /**
     * Indicates whether the draft contains all required fields for ticket creation.
     *
     * @return {@code true} when summary, priority, and details have been supplied
     */
    public boolean isComplete() {
        return values.containsKey(Step.SUMMARY)
                && values.containsKey(Step.PRIORITY)
                && values.containsKey(Step.DETAILS);
    }

    /**
     * Exposes an immutable view of the captured step values.
     *
     * @return read-only map of step values
     */
    public Map<Step, String> asMap() {
        return Map.copyOf(values);
    }

    /**
     * Associates the draft with a persisted ticket identifier.
     *
     * @param ticketId id of the ticket record backing this draft
     */
    public void setTicketId(Long ticketId) {
        this.ticketId = ticketId;
    }

    /**
     * Returns the ticket id linked to the draft.
     *
     * @return associated ticket identifier, or {@code null} if not yet persisted
     */
    public Long getTicketId() {
        return ticketId;
    }
}
