package com.salex.telegram.Ticketing;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TicketPriorityTest {

    @Test
    void priorityEnumExposesExpectedLevels() {
        assertThat(TicketPriority.values())
                .containsExactly(
                        TicketPriority.LOW,
                        TicketPriority.MEDIUM,
                        TicketPriority.HIGH,
                        TicketPriority.URGENT
                );
    }
}
