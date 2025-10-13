package com.salex.telegram.ticketing;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/**
 * Captures partially completed ticket data during multi-step user interaction.
 */
public final class TicketDraft {
    public enum Step {
        SUMMARY,
        PRIORITY,
        DETAILS,
        CONFIRMATION
    }

    private final Map<Step, String> values = new EnumMap<>(Step.class);

    public void put(Step step, String value) {
        Objects.requireNonNull(value, "value");
        values.put(step, value);
    }

    public String get(Step step) {
        return values.get(step);
    }

    public boolean isComplete() {
        return values.containsKey(Step.SUMMARY)
                && values.containsKey(Step.PRIORITY)
                && values.containsKey(Step.DETAILS);
    }

    public Map<Step, String> asMap() {
        return Map.copyOf(values);
    }
}
