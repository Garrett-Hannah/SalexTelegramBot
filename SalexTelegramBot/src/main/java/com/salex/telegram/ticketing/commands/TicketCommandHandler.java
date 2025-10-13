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

    public TicketCommandHandler(TicketService ticketService, TicketMessageFormatter formatter) {
        this.ticketService = ticketService;
        this.formatter = formatter;
    }

    @Override
    public String getName() {
        return "/ticket";
    }

    @Override
    public String getDescription() {
        return "Manage support tickets.";
    }

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

    private void handleNewTicket(long chatId, long userId, TelegramBot bot) {
        Ticket ticket = ticketService.startTicketCreation(chatId, userId);
        bot.sendMessage(chatId, formatter.formatCreationPrompt(ticket));
        Optional<TicketDraft.Step> next = ticketService.getActiveStep(chatId, userId);
        next.ifPresent(step -> bot.sendMessage(chatId, formatter.formatNextStepPrompt(step)));
    }

    private void handleListTickets(long chatId, long userId, TelegramBot bot) {
        List<Ticket> tickets = ticketService.listTicketsForUser(userId);
        bot.sendMessage(chatId, formatter.formatTicketList(tickets));
    }

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

    private void handleTicketLookup(String[] tokens, long chatId, long userId, TelegramBot bot) {
        long ticketId = parseTicketId(tokens[1]);
        ticketService.getTicket(ticketId, userId)
                .ifPresentOrElse(
                        ticket -> bot.sendMessage(chatId, formatter.formatTicketCard(ticket)),
                        () -> bot.sendMessage(chatId, formatter.formatNotFound(ticketId))
                );
    }

    private long parseTicketId(String token) {
        try {
            return Long.parseLong(token);
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("Ticket id must be a number.");
        }
    }
}
