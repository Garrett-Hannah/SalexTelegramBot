package com.salex.telegram.ticketing;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/**
 * Coordinates validation, persistence and command messaging for tickets.
 */
public class TicketService {
    private final TicketRepository repository;
    private final TicketSessionManager sessionManager;

    public TicketService(TicketRepository repository, TicketSessionManager sessionManager) {
        this.repository = repository;
        this.sessionManager = sessionManager;
    }

    public Ticket startTicketCreation(long chatId, long userId) {
        if (sessionManager.getDraft(chatId, userId).isPresent()) {
            throw new IllegalStateException("Ticket creation already in progress");
        }

        sessionManager.openSession(chatId, userId);
        TicketDraft draft = new TicketDraft();

        Instant now = Instant.now();
        Ticket ticketToPersist = Ticket.builder()
                .status(TicketStatus.OPEN)
                .priority(TicketPriority.MEDIUM)
                .createdAt(now)
                .updatedAt(now)
                .createdBy(userId)
                .assignee(null)
                .summary("")
                .details("")
                .build();

        Ticket persisted = repository.createDraftTicket(ticketToPersist);
        draft.setTicketId(persisted.getId());
        sessionManager.updateDraft(chatId, userId, draft);

        return persisted;
    }

    public Ticket collectTicketField(long chatId, long userId, String messageText) {
        TicketDraft draft = sessionManager.getDraft(chatId, userId)
                .orElseThrow(() -> new IllegalStateException("No active ticket session"));

        Long ticketId = draft.getTicketId();
        if (ticketId == null) {
            throw new IllegalStateException("Draft missing ticket reference");
        }

        Ticket ticket = repository.findById(ticketId)
                .orElseThrow(() -> new IllegalStateException("Ticket not found"));

        TicketDraft.Step nextStep = determineNextStep(draft);
        if (nextStep == null) {
            throw new IllegalStateException("Ticket draft already complete");
        }

        String sanitizedInput = sanitizeInput(messageText);
        Ticket updatedTicket;

        switch (nextStep) {
            case SUMMARY -> {
                draft.put(TicketDraft.Step.SUMMARY, sanitizedInput);
                sessionManager.updateDraft(chatId, userId, draft);
                updatedTicket = repository.save(updateTicket(ticket, builder ->
                        builder.summary(sanitizedInput)));
            }
            case PRIORITY -> {
                TicketPriority priority = parsePriority(sanitizedInput);
                draft.put(TicketDraft.Step.PRIORITY, priority.name());
                sessionManager.updateDraft(chatId, userId, draft);
                updatedTicket = repository.save(updateTicket(ticket, builder ->
                        builder.priority(priority)));
            }
            case DETAILS -> {
                draft.put(TicketDraft.Step.DETAILS, sanitizedInput);
                sessionManager.updateDraft(chatId, userId, draft);
                sessionManager.closeSession(chatId, userId);
                updatedTicket = repository.save(updateTicket(ticket, builder ->
                        builder.details(sanitizedInput)));
            }
            default -> throw new IllegalStateException("Unhandled step: " + nextStep);
        }

        return updatedTicket;
    }

    public Optional<Ticket> getTicket(long ticketId, long userId) {
        return repository.findById(ticketId)
                .filter(ticket -> ticket.getCreatedBy() == userId
                        || (ticket.getAssignee() != null && ticket.getAssignee().equals(userId)));
    }

    public List<Ticket> listTicketsForUser(long userId) {
        return repository.findAllForUser(userId);
    }

    public Ticket closeTicket(long ticketId, long userId, String resolutionNote) {
        Ticket ticket = repository.findById(ticketId)
                .orElseThrow(() -> new IllegalStateException("Ticket not found"));

        if (!isActionAuthorised(ticket, userId)) {
            throw new IllegalStateException("User cannot close this ticket");
        }

        Ticket closedTicket = updateTicket(ticket, builder -> builder
                .status(TicketStatus.CLOSED)
                .details(appendResolution(ticket.getDetails(), resolutionNote)));

        return repository.save(closedTicket);
    }

    private TicketDraft.Step determineNextStep(TicketDraft draft) {
        if (draft.get(TicketDraft.Step.SUMMARY) == null) {
            return TicketDraft.Step.SUMMARY;
        }
        if (draft.get(TicketDraft.Step.PRIORITY) == null) {
            return TicketDraft.Step.PRIORITY;
        }
        if (draft.get(TicketDraft.Step.DETAILS) == null) {
            return TicketDraft.Step.DETAILS;
        }
        return null;
    }

    private TicketPriority parsePriority(String input) {
        String normalized = input.toUpperCase();
        try {
            return TicketPriority.valueOf(normalized);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Unknown priority: " + input);
        }
    }

    private String sanitizeInput(String value) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException("Input cannot be empty");
        }
        return value.trim();
    }

    private Ticket updateTicket(Ticket ticket, Function<Ticket.Builder, Ticket.Builder> mutator) {
        Ticket.Builder builder = ticket.toBuilder()
                .updatedAt(Instant.now());
        return mutator.apply(builder).build();
    }

    private boolean isActionAuthorised(Ticket ticket, long userId) {
        return ticket.getCreatedBy() == userId
                || (ticket.getAssignee() != null && ticket.getAssignee().equals(userId));
    }

    private String appendResolution(String details, String resolutionNote) {
        if (resolutionNote == null || resolutionNote.trim().isEmpty()) {
            return details;
        }
        String trimmed = resolutionNote.trim();
        if (details == null || details.isEmpty()) {
            return "Resolution: " + trimmed;
        }
        return details + System.lineSeparator() + System.lineSeparator() + "Resolution: " + trimmed;
    }
}
