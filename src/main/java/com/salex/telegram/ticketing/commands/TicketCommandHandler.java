package com.salex.telegram.ticketing.commands;

import com.salex.telegram.bot.SalexTelegramBot;
import com.salex.telegram.ticketing.Ticket;
import com.salex.telegram.ticketing.TicketDraft;
import com.salex.telegram.ticketing.TicketService;
import com.salex.telegram.modules.CommandHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.telegram.telegrambots.meta.api.objects.Update;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Dispatches ticket-related commands invoked from Telegram into service calls.
 */
public class TicketCommandHandler implements CommandHandler {
    private static final Logger log = LoggerFactory.getLogger(TicketCommandHandler.class);
    private final TicketService ticketService;
    private final TicketMessageFormatter formatter;

    /**
     * Creates a handler for ticket commands.
     *
     * @param ticketService backing ticket service
     * @param formatter     helper used to format responses for Telegram
     */
    public TicketCommandHandler(TicketService ticketService, TicketMessageFormatter formatter) {
        this.ticketService = ticketService;
        this.formatter = formatter;
        log.info("TicketCommandHandler initialised");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getName() {
        return "/ticket";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getDescription() {
        return "Manage support tickets.";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void handle(Update update, SalexTelegramBot bot, long userId) {
        if (!update.hasMessage() || !update.getMessage().hasText()) {
            return;
        }

        long chatId = update.getMessage().getChatId();
        Integer threadId = update.getMessage().getMessageThreadId();
        String text = update.getMessage().getText().trim();

        String[] tokens = text.split("\\s+", 3);
        if (tokens.length == 1) {
            bot.sendMessage(chatId, threadId, formatter.formatHelp());
            log.debug("Ticket help requested by user {}", userId);
            return;
        }

        String subCommand = tokens[1].toLowerCase(Locale.ROOT);

        try {
            switch (subCommand) {
                case "new" -> handleNewTicket(chatId, threadId, userId, bot);
                case "list" -> handleListTickets(chatId, threadId, userId, bot);
                case "close" -> handleCloseTicket(tokens, chatId, threadId, userId, bot);
                case "help" -> bot.sendMessage(chatId, threadId, formatter.formatHelp());
                default -> handleTicketLookup(tokens, chatId, threadId, userId, bot);
            }
        } catch (IllegalArgumentException | IllegalStateException ex) {
            bot.sendMessage(chatId, threadId, formatter.formatError(ex.getMessage()));
            log.error("Ticket command '{}' failed for user {}: {}", subCommand, userId, ex.getMessage(), ex);
        }
    }

    /**
     * Starts a ticket creation workflow and prompts the user for subsequent steps.
     *
     * @param chatId chat where the command originated
     * @param userId internal user identifier
     * @param bot    bot instance used to send replies
     */
    private void handleNewTicket(long chatId, Integer threadId, long userId, SalexTelegramBot bot) {
        Ticket ticket = ticketService.startTicketCreation(chatId, userId);
        log.info("User {} started ticket workflow for ticket {}", userId, ticket.getId());
        bot.sendMessage(chatId, threadId, formatter.formatCreationPrompt(ticket));
        Optional<TicketDraft.Step> next = ticketService.getActiveStep(chatId, userId);
        next.ifPresent(step -> bot.sendMessage(chatId, threadId, formatter.formatNextStepPrompt(step)));
    }

    /**
     * Lists all tickets accessible by the invoking user.
     *
     * @param chatId chat where the command originated
     * @param userId internal user identifier
     * @param bot    bot instance used to send replies
     */
    private void handleListTickets(long chatId, Integer threadId, long userId, SalexTelegramBot bot) {
        List<Ticket> tickets = ticketService.listTicketsForUser(userId);
        log.info("User {} requested ticket list ({} items)", userId, tickets.size());
        bot.sendMessage(chatId, threadId, formatter.formatTicketList(tickets));

        //TODO:  maybe interact with chat context to append a thing before to allow the use in relation. or like give a
        //Recommendation on whats important.

        //TODO: change interaction so closed tickets will not list.
        //if there is a long time after close...
    }

    /**
     * Closes the specified ticket and relays confirmation messages.
     *
     * @param tokens command tokens containing the ticket id and optional resolution
     * @param chatId chat where the command originated
     * @param userId internal user identifier
     * @param bot    bot instance used to send replies
     */
    private void handleCloseTicket(String[] tokens, long chatId, Integer threadId, long userId, SalexTelegramBot bot) {
        if (tokens.length < 3) {
            throw new IllegalArgumentException("Provide the ticket id and a resolution note.");
        }

        String[] params = tokens[2].split("\\s+", 2);
        long ticketId = parseTicketId(params[0]);
        String resolution = params.length > 1 ? params[1] : "";

        Ticket closed = ticketService.closeTicket(ticketId, userId, resolution);
        log.info("User {} closed ticket {}", userId, ticketId);
        bot.sendMessage(chatId, threadId, formatter.formatClosurePrompt(closed));
        bot.sendMessage(chatId, threadId, formatter.formatClosingConfirmation(closed));
    }

    /**
     * Retrieves and displays a single ticket using the supplied command tokens.
     *
     * @param tokens command tokens containing the ticket id
     * @param chatId chat where the command originated
     * @param userId internal user identifier
     * @param bot    bot instance used to send replies
     */
    private void handleTicketLookup(String[] tokens, long chatId, Integer threadId, long userId, SalexTelegramBot bot) {
        long ticketId = parseTicketId(tokens[1]);
        ticketService.getTicket(ticketId, userId)
                .ifPresentOrElse(
                        ticket -> bot.sendMessage(chatId, threadId, formatter.formatTicketCard(ticket)),
                        () -> bot.sendMessage(chatId, threadId, formatter.formatNotFound(ticketId))
                );
        log.debug("User {} looked up ticket {}", userId, ticketId);
    }

    /**
     * Parses a ticket id from the user-provided token.
     *
     * @param token raw token containing the ticket id
     * @return the parsed ticket identifier
     * @throws IllegalArgumentException if the token is not numeric
     */
    private long parseTicketId(String token) {
        try {
            return Long.parseLong(token);
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("Ticket id must be a number.");
        }
    }
}
