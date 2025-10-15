package com.salex.telegram.Ticketing;

import com.salex.telegram.Ticketing.TicketDraft.Step;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TicketDraftTest {

    @Test
    void draftStoresValuesPerStep() {
        TicketDraft draft = new TicketDraft();
        draft.put(Step.SUMMARY, "Issue summary");
        draft.put(Step.DETAILS, "Detailed repro steps");

        assertThat(draft.get(Step.SUMMARY)).isEqualTo("Issue summary");
        assertThat(draft.get(Step.DETAILS)).isEqualTo("Detailed repro steps");
        assertThat(draft.isComplete()).isFalse();
    }

    @Test
    void draftTracksTicketId() {
        TicketDraft draft = new TicketDraft();
        draft.setTicketId(42L);

        assertThat(draft.getTicketId()).isEqualTo(42L);
    }

    @Test
    void draftReportsCompletionWhenAllFieldsPresent() {
        TicketDraft draft = new TicketDraft();
        draft.put(Step.SUMMARY, "Printer offline");
        draft.put(Step.PRIORITY, TicketPriority.HIGH.name());
        draft.put(Step.DETAILS, "Device 4F is down");

        assertThat(draft.isComplete()).isTrue();
        assertThat(draft.asMap()).containsEntry(Step.PRIORITY, TicketPriority.HIGH.name());
    }
}
