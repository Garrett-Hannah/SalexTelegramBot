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

    public String formatCreationPrompt(Ticket ticket) {
        return "Ticket #" + ticket.getId()
                + " created. We need a summary, priority, and details.";
    }

    public String formatNextStepPrompt(TicketDraft.Step step) {
        return switch (step) {
            case SUMMARY -> "Please provide a short summary for this ticket.";
            case PRIORITY -> "Set the priority (low, medium, high, urgent).";
            case DETAILS -> "Share the detailed description or reproduction steps.";
            case CONFIRMATION -> "Confirm the ticket details.";
        };
    }

    public String formatStepAcknowledgement(TicketDraft.Step step, Ticket ticket) {
        return switch (step) {
            case SUMMARY -> "Summary saved: " + ticket.getSummary();
            case PRIORITY -> "Priority set to " + ticket.getPriority();
            case DETAILS -> "Details captured.";
            case CONFIRMATION -> "Confirmation received.";
        };
    }

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

    public String formatClosurePrompt(Ticket ticket) {
        return "Closing ticket #" + ticket.getId() + ". Capturing resolution.";
    }

    public String formatNotFound(long ticketId) {
        return "Ticket #" + ticketId + " was not found or you do not have access.";
    }

    public String formatHelp() {
        return String.join(System.lineSeparator(),
                "Use the ticket commands:",
                "/ticket new - start a ticket",
                "/ticket list - list your tickets",
                "/ticket <id> - show a ticket",
                "/ticket close <id> <note> - close a ticket");
    }

    public String formatError(String message) {
        return "[Error] " + message;
    }

    public String formatClosingConfirmation(Ticket ticket) {
        return "Ticket #" + ticket.getId() + " closed.";
    }

    public String formatCreationComplete(Ticket ticket) {
        return "Ticket #" + ticket.getId() + " is ready." + System.lineSeparator()
                + formatTicketCard(ticket);
    }
}
