package com.salex.telegram.ticketing.commands;

import com.salex.telegram.Bot.TelegramBot;
import com.salex.telegram.commanding.CommandHandler;
import com.salex.telegram.ticketing.Ticket;
import com.salex.telegram.ticketing.TicketDraft;
import com.salex.telegram.ticketing.TicketService;
import org.telegram.telegrambots.meta.api.objects.Update;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Dispatches ticket-related commands invoked from Telegram into service calls.
 */
public class TicketCommandHandler implements CommandHandler {
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
    public void handle(Update update, TelegramBot bot, long userId) {
        if (!update.hasMessage() || !update.getMessage().hasText()) {
            return;
        }

        long chatId = update.getMessage().getChatId();
        String text = update.getMessage().getText().trim();

        String[] tokens = text.split("\\s+", 3);
        if (tokens.length == 1) {
            bot.sendMessage(chatId, formatter.formatHelp());
            return;
        }

        String subCommand = tokens[1].toLowerCase(Locale.ROOT);

        try {
            switch (subCommand) {
                case "new" -> handleNewTicket(chatId, userId, bot);
                case "list" -> handleListTickets(chatId, userId, bot);
                case "close" -> handleCloseTicket(tokens, chatId, userId, bot);
                case "help" -> bot.sendMessage(chatId, formatter.formatHelp());
                default -> handleTicketLookup(tokens, chatId, userId, bot);
            }
        } catch (IllegalArgumentException | IllegalStateException ex) {
            bot.sendMessage(chatId, formatter.formatError(ex.getMessage()));
        }
    }

    /**
     * Starts a ticket creation workflow and prompts the user for subsequent steps.
     *
     * @param chatId chat where the command originated
     * @param userId internal user identifier
     * @param bot    bot instance used to send replies
     */
    private void handleNewTicket(long chatId, long userId, TelegramBot bot) {
        Ticket ticket = ticketService.startTicketCreation(chatId, userId);
        bot.sendMessage(chatId, formatter.formatCreationPrompt(ticket));
        Optional<TicketDraft.Step> next = ticketService.getActiveStep(chatId, userId);
        next.ifPresent(step -> bot.sendMessage(chatId, formatter.formatNextStepPrompt(step)));
    }

    /**
     * Lists all tickets accessible by the invoking user.
     *
     * @param chatId chat where the command originated
     * @param userId internal user identifier
     * @param bot    bot instance used to send replies
     */
    private void handleListTickets(long chatId, long userId, TelegramBot bot) {
        List<Ticket> tickets = ticketService.listTicketsForUser(userId);
        bot.sendMessage(chatId, formatter.formatTicketList(tickets));
    }

    /**
     * Closes the specified ticket and relays confirmation messages.
     *
     * @param tokens command tokens containing the ticket id and optional resolution
     * @param chatId chat where the command originated
     * @param userId internal user identifier
     * @param bot    bot instance used to send replies
     */
    private void handleCloseTicket(String[] tokens, long chatId, long userId, TelegramBot bot) {
        if (tokens.length < 3) {
            throw new IllegalArgumentException("Provide the ticket id and a resolution note.");
        }

        String[] params = tokens[2].split("\\s+", 2);
        long ticketId = parseTicketId(params[0]);
        String resolution = params.length > 1 ? params[1] : "";

        Ticket closed = ticketService.closeTicket(ticketId, userId, resolution);
        bot.sendMessage(chatId, formatter.formatClosurePrompt(closed));
        bot.sendMessage(chatId, formatter.formatClosingConfirmation(closed));
    }

    /**
     * Retrieves and displays a single ticket using the supplied command tokens.
     *
     * @param tokens command tokens containing the ticket id
     * @param chatId chat where the command originated
     * @param userId internal user identifier
     * @param bot    bot instance used to send replies
     */
    private void handleTicketLookup(String[] tokens, long chatId, long userId, TelegramBot bot) {
        long ticketId = parseTicketId(tokens[1]);
        ticketService.getTicket(ticketId, userId)
                .ifPresentOrElse(
                        ticket -> bot.sendMessage(chatId, formatter.formatTicketCard(ticket)),
                        () -> bot.sendMessage(chatId, formatter.formatNotFound(ticketId))
                );
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
