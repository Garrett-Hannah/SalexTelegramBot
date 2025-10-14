package com.salex.telegram.ticketing.commands;

import com.salex.telegram.Bot.TelegramBot;
import com.salex.telegram.ticketing.InMemory.InMemoryTicketRepository;
import com.salex.telegram.ticketing.InMemory.InMemoryTicketSessionManager;
import com.salex.telegram.ticketing.Ticket;
import com.salex.telegram.ticketing.TicketService;
import org.junit.jupiter.api.Test;

import org.telegram.telegrambots.meta.api.objects.Chat;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.User;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TicketCommandHandlerTest {

    private final TicketService service = new TicketService(new InMemoryTicketRepository(), new InMemoryTicketSessionManager());
    private final TicketMessageFormatter formatter = new TicketMessageFormatter();
    private final TicketCommandHandler handler = new TicketCommandHandler(service, formatter);

    @Test
    void handleNewTicketStartsWorkflow() {
        TestTelegramBot bot = new TestTelegramBot(service, formatter);
        Update update = buildUpdate(1L, 100L, "/ticket new");

        handler.handle(update, bot, 100L);

        assertThat(bot.messages).hasSize(2);
        assertThat(bot.messages.get(0).text()).contains("Ticket #1");
        assertThat(bot.messages.get(1).text()).contains("summary");
    }

    @Test
    void handleTicketLookupShowsTicketCard() {
        long chatId = 2L;
        long userId = 200L;
        Ticket ticket = service.startTicketCreation(chatId, userId);
        service.collectTicketField(chatId, userId, "API outage");
        service.collectTicketField(chatId, userId, "high");
        service.collectTicketField(chatId, userId, "Details");

        TestTelegramBot bot = new TestTelegramBot(service, formatter);
        handler.handle(buildUpdate(chatId, userId, "/ticket " + ticket.getId()), bot, userId);

        assertThat(bot.messages).hasSize(1);
        assertThat(bot.messages.get(0).text()).contains("Ticket #" + ticket.getId());
    }

    @Test
    void handleListTicketsSummarisesEntries() {
        long chatId = 3L;
        long userId = 300L;
        Ticket ticket = service.startTicketCreation(chatId, userId);
        service.collectTicketField(chatId, userId, "Summary");
        service.collectTicketField(chatId, userId, "low");
        service.collectTicketField(chatId, userId, "Details");

        TestTelegramBot bot = new TestTelegramBot(service, formatter);
        handler.handle(buildUpdate(chatId, userId, "/ticket list"), bot, userId);

        assertThat(bot.messages).hasSize(1);
        assertThat(bot.messages.get(0).text()).contains("#" + ticket.getId());
    }

    @Test
    void handleCloseTicketClosesAndConfirms() {
        long chatId = 4L;
        long userId = 400L;
        Ticket ticket = service.startTicketCreation(chatId, userId);
        service.collectTicketField(chatId, userId, "Summary");
        service.collectTicketField(chatId, userId, "urgent");
        service.collectTicketField(chatId, userId, "Details");

        TestTelegramBot bot = new TestTelegramBot(service, formatter);
        handler.handle(buildUpdate(chatId, userId, "/ticket close " + ticket.getId() + " Patched"), bot, userId);

        assertThat(bot.messages).hasSize(2);
        assertThat(bot.messages.get(1).text()).contains("closed");
        assertThat(service.getTicket(ticket.getId(), userId)).isPresent()
                .get()
                .extracting(Ticket::getStatus)
                .isEqualTo(com.salex.telegram.ticketing.TicketStatus.CLOSED);
    }

    private Update buildUpdate(long chatId, long userId, String text) {
        Update update = new Update();
        Message message = new Message();
        message.setText(text);
        Chat chat = new Chat();
        chat.setId(chatId);
        message.setChat(chat);
        User user = new User();
        user.setId(userId);
        message.setFrom(user);
        update.setMessage(message);
        return update;
    }

    private static final class TestTelegramBot extends TelegramBot {
        private final List<LoggedMessage> messages = new ArrayList<>();

        TestTelegramBot(TicketService service, TicketMessageFormatter formatter) {
            super("token", "debug-bot", , service, formatter);
        }

        @Override
        protected void sendMessage(long chatId, String text) {
            messages.add(new LoggedMessage(chatId, text));
        }
    }

    private record LoggedMessage(long chatId, String text) {
    }
}
