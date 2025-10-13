package com.salex.telegram.ticketing;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TicketStatusTest {

    @Test
    void statusEnumIncludesLifecycleStates() {
        assertThat(TicketStatus.values())
                .containsExactly(
                        TicketStatus.OPEN,
                        TicketStatus.IN_PROGRESS,
                        TicketStatus.CLOSED
                );
    }
}
