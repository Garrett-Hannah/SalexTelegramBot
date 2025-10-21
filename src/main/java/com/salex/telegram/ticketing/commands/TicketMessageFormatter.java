package com.salex.telegram.ticketing.commands;

import com.salex.telegram.ticketing.Ticket;
import com.salex.telegram.ticketing.TicketDraft;

import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Provides reusable Telegram-friendly message formats for ticket updates.
 */
public class TicketMessageFormatter {
    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ISO_INSTANT;

    /**
     * Builds the initial prompt sent after starting ticket creation.
     *
     * @param ticket the newly created ticket
     * @return message instructing the user on next inputs
     */
    public String formatCreationPrompt(Ticket ticket) {
        return "Ticket #" + ticket.getId()
                + " created. We need a summary, priority, and details.";
    }

    /**
     * Provides guidance on the next required ticket step.
     *
     * @param step the pending draft step
     * @return user-facing instruction
     */
    public String formatNextStepPrompt(TicketDraft.Step step) {
        return switch (step) {
            case SUMMARY -> "Please provide a short summary for this ticket.";
            case PRIORITY -> "Set the priority (low, medium, high, urgent).";
            case DETAILS -> "Share the detailed description or reproduction steps.";
            case CONFIRMATION -> "Confirm the ticket details.";
        };
    }

    /**
     * Acknowledges that a ticket step was captured successfully.
     *
     * @param step   the step that was just processed
     * @param ticket the latest ticket state
     * @return acknowledgement message
     */
    public String formatStepAcknowledgement(TicketDraft.Step step, Ticket ticket) {
        return switch (step) {
            case SUMMARY -> "Summary saved: " + ticket.getSummary();
            case PRIORITY -> "Priority set to " + ticket.getPriority();
            case DETAILS -> "Details captured.";
            case CONFIRMATION -> "Confirmation received.";
        };
    }

    /**
     * Renders a formatted summary card for a single ticket.
     *
     * @param ticket the ticket to present
     * @return formatted ticket card text
     */
    public String formatTicketCard(Ticket ticket) {
        StringBuilder builder = new StringBuilder();
        builder.append("Ticket #").append(ticket.getId()).append(System.lineSeparator());
        builder.append("Status: ").append(ticket.getStatus()).append(System.lineSeparator());
        builder.append("Priority: ").append(ticket.getPriority()).append(System.lineSeparator());
        builder.append("Created: ").append(DATE_FORMAT.format(ticket.getCreatedAt())).append(System.lineSeparator());
        builder.append("Updated: ").append(DATE_FORMAT.format(ticket.getUpdatedAt())).append(System.lineSeparator());
        builder.append("Summary: ").append(ticket.getSummary()).append(System.lineSeparator());
        builder.append("Details: ").append(ticket.getDetails());
        return builder.toString();
    }

    /**
     * Lists tickets owned by the user in a concise form.
     *
     * @param tickets tickets visible to the user
     * @return formatted list suitable for Telegram
     */
    public String formatTicketList(List<Ticket> tickets) {
        if (tickets.isEmpty()) {
            return "You have no tickets yet.";
        }
        StringBuilder builder = new StringBuilder("Your tickets:\n");
        for (Ticket ticket : tickets) {
            builder.append("#")
                    .append(ticket.getId())
                    .append(" [")
                    .append(ticket.getStatus())
                    .append("] ")
                    .append(ticket.getSummary())
                    .append(System.lineSeparator());
        }
        return builder.toString().trim();
    }

    /**
     * Generates a message indicating that a ticket is being closed.
     *
     * @param ticket the ticket that is closing
     * @return closing prompt
     */
    public String formatClosurePrompt(Ticket ticket) {
        return "Closing ticket #" + ticket.getId() + ". Capturing resolution.";
    }

    /**
     * Builds a not-found message for a ticket lookup.
     *
     * @param ticketId id that could not be located
     * @return not-found text
     */
    public String formatNotFound(long ticketId) {
        return "Ticket #" + ticketId + " was not found or you do not have access.";
    }

    /**
     * Returns usage information for all supported ticket commands.
     *
     * @return help text describing sub-commands
     */
    public String formatHelp() {
        return String.join(System.lineSeparator(),
                "Use the ticket commands:",
                "/ticket new - start a ticket",
                "/ticket list - list your tickets",
                "/ticket <id> - show a ticket",
                "/ticket close <id> <note> - close a ticket");
    }

    /**
     * Wraps an error message in a consistent prefix.
     *
     * @param message underlying error detail
     * @return formatted error
     */
    public String formatError(String message) {
        return "[Error] " + message;
    }

    /**
     * Returns confirmation text after a ticket is closed.
     *
     * @param ticket the closed ticket
     * @return closing confirmation message
     */
    public String formatClosingConfirmation(Ticket ticket) {
        return "Ticket #" + ticket.getId() + " closed.";
    }

    /**
     * Produces a summary message signalling ticket creation completion.
     *
     * @param ticket fully populated ticket
     * @return creation completion message including the full ticket card
     */
    public String formatCreationComplete(Ticket ticket) {
        return "Ticket #" + ticket.getId() + " is ready." + System.lineSeparator()
                + formatTicketCard(ticket);
    }
}
