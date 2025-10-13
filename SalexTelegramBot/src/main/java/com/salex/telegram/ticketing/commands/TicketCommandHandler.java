package com.salex.telegram.ticketing.commands;

import com.salex.telegram.Bot.TelegramBot;
import com.salex.telegram.commanding.CommandHandler;
import com.salex.telegram.ticketing.TicketService;
import com.salex.telegram.ticketing.Ticket;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;

/**
 * Dispatches ticket-related commands invoked from Telegram into service calls.
 */
public class TicketCommandHandler implements CommandHandler {
    private final TicketService ticketService;

    public TicketCommandHandler(TicketService ticketService) {
        this.ticketService = ticketService;
    }

    public void handleNewTicketCommand(long chatId, long userId) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    public void handleTicketLookup(long chatId, long userId, long ticketId) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    public void handleListTickets(long chatId, long userId) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    public void handleAddNote(long chatId, long userId, long ticketId, String note) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    public void handleCloseTicket(long chatId, long userId, long ticketId, String confirmation) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public String getName () {
        return "/ticket";
    }

    @Override
    public String getDescription () {
        return "Print Ticket:" +
                "Help: idk this is debug right now";
    }

    @Override
    public void handle (Update update, TelegramBot bot) {
        long telegramId = update.getMessage().getFrom().getId();
        Long chatID = update.getMessage().getChatId();

        String text = update.getMessage().getText();

        String descr = text.replaceFirst(getName(), "").trim();
        SendMessage sendMessage = new SendMessage(chatID.toString(), descr);
        //ticketService.startTicketCreation(telegramId, user)

    }
}
