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

    /**
     * Creates a service that orchestrates ticket lifecycle operations.
     *
     * @param repository     backing ticket repository
     * @param sessionManager manager tracking interactive draft sessions
     */
    public TicketService(TicketRepository repository, TicketSessionManager sessionManager) {
        this.repository = repository;
        this.sessionManager = sessionManager;
    }

    /**
     * Begins an interactive ticket creation workflow for the user in the given chat.
     *
     * @param chatId chat initiating the workflow
     * @param userId user creating the ticket
     * @return persisted ticket placeholder
     * @throws IllegalStateException if a draft already exists for the user in the chat
     */
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

    /**
     * Applies a user-provided field update to the current ticket draft.
     *
     * @param chatId      chat that owns the draft session
     * @param userId      user owning the draft session
     * @param messageText raw user input
     * @return updated ticket instance
     * @throws IllegalStateException    if no session exists or the draft is invalid
     * @throws IllegalArgumentException if the user input fails validation
     */
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

    /**
     * Retrieves a ticket if the user has permission to view it.
     *
     * @param ticketId ticket identifier
     * @param userId   user attempting to view the ticket
     * @return ticket if accessible, otherwise empty
     */
    public Optional<Ticket> getTicket(long ticketId, long userId) {
        return repository.findById(ticketId)
                .filter(ticket -> ticket.getCreatedBy() == userId
                        || (ticket.getAssignee() != null && ticket.getAssignee().equals(userId)));
    }

    /**
     * Lists all tickets associated with the user.
     *
     * @param userId user whose tickets should be fetched
     * @return tickets owned or visible to the user
     */
    public List<Ticket> listTicketsForUser(long userId) {
        return repository.findAllForUser(userId);
    }

    /**
     * Closes a ticket and appends a resolution note when authorised.
     *
     * @param ticketId       ticket identifier
     * @param userId         user attempting to close the ticket
     * @param resolutionNote optional resolution details
     * @return updated ticket marked as closed
     * @throws IllegalStateException if the ticket is not found or the user is not authorised
     */
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

    /**
     * Determines whether a draft session is active for the provided chat and user.
     *
     * @param chatId chat identifier
     * @param userId user identifier
     * @return {@code true} if a draft exists, otherwise {@code false}
     */
    public boolean hasActiveDraft(long chatId, long userId) {
        return sessionManager.getDraft(chatId, userId).isPresent();
    }

    /**
     * Returns the next required step for an active ticket draft.
     *
     * @param chatId chat identifier
     * @param userId user identifier
     * @return optional containing the next step, or empty if no session exists or no steps remain
     */
    public Optional<TicketDraft.Step> getActiveStep(long chatId, long userId) {
        Optional<TicketDraft> draft = sessionManager.getDraft(chatId, userId);
        if (draft.isEmpty()) {
            return Optional.empty();
        }
        return Optional.ofNullable(determineNextStep(draft.get()));
    }

    /**
     * Determines which ticket draft step is pending.
     *
     * @param draft current draft values
     * @return next missing step, or {@code null} if complete
     */
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

    /**
     * Validates and converts a user-supplied priority value.
     *
     * @param input raw priority input
     * @return corresponding {@link TicketPriority}
     * @throws IllegalArgumentException if the priority value is not recognised
     */
    private TicketPriority parsePriority(String input) {
        String normalized = input.toUpperCase();
        try {
            return TicketPriority.valueOf(normalized);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Unknown priority: " + input);
        }
    }

    /**
     * Sanitises user input by trimming and ensuring content is not empty.
     *
     * @param value raw user input
     * @return cleaned text
     * @throws IllegalArgumentException if the input is blank
     */
    private String sanitizeInput(String value) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException("Input cannot be empty");
        }
        return value.trim();
    }

    /**
     * Applies a mutation to a ticket builder while updating timestamps.
     *
     * @param ticket  original ticket
     * @param mutator function that mutates the builder
     * @return updated ticket instance
     */
    private Ticket updateTicket(Ticket ticket, Function<Ticket.Builder, Ticket.Builder> mutator) {
        Ticket.Builder builder = ticket.toBuilder()
                .updatedAt(Instant.now());
        return mutator.apply(builder).build();
    }

    /**
     * Verifies whether the user is allowed to perform actions on the ticket.
     *
     * @param ticket ticket being acted upon
     * @param userId user requesting the action
     * @return {@code true} if the user created or is assigned to the ticket
     */
    private boolean isActionAuthorised(Ticket ticket, long userId) {
        return ticket.getCreatedBy() == userId
                || (ticket.getAssignee() != null && ticket.getAssignee().equals(userId));
    }

    /**
     * Appends a resolution note to the ticket details.
     *
     * @param details        original ticket details
     * @param resolutionNote resolution text to add
     * @return combined details with resolution appended when provided
     */
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
