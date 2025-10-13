package com.salex.telegram.Bot;

import com.salex.telegram.commanding.CommandHandler;
import com.salex.telegram.ticketing.InMemory.InMemoryTicketRepository;
import com.salex.telegram.ticketing.InMemory.InMemoryTicketSessionManager;
import com.salex.telegram.ticketing.Ticket;
import com.salex.telegram.ticketing.TicketDraft;
import com.salex.telegram.ticketing.TicketService;
import com.salex.telegram.ticketing.commands.TicketCommandHandler;
import com.salex.telegram.ticketing.commands.TicketMessageFormatter;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Telegram bot implementation that dispatches commands, manages ticket workflows,
 * and forwards free-form messages to a language model.
 */
public class TelegramBot extends TelegramLongPollingBot {
    private final String username;
    private final Connection conn;
    private final TicketService ticketService;
    private final TicketMessageFormatter ticketFormatter;
    private final Map<String, CommandHandler> commands = new HashMap<>();

    /**
     * Creates a bot that uses in-memory ticket backing services for lightweight deployments.
     *
     * @param token    bot token provided by Telegram
     * @param username bot public username
     * @param conn     JDBC connection used for persistence (may be {@code null} for in-memory usage)
     */
    public TelegramBot(String token, String username, Connection conn) {
        this(token, username, conn,
                new TicketService(new InMemoryTicketRepository(), new InMemoryTicketSessionManager()),
                new TicketMessageFormatter());
    }

    /**
     * Creates a bot with explicit ticket service and formatter dependencies.
     *
     * @param token           bot token provided by Telegram
     * @param username        bot public username
     * @param conn            JDBC connection used for persistence (may be {@code null})
     * @param ticketService   service handling ticket lifecycle operations (may be {@code null} for disabled ticketing)
     * @param ticketFormatter formatter used for rendering ticket messages (may be {@code null} for disabled ticketing)
     */
    public TelegramBot(String token, String username, Connection conn,
                       TicketService ticketService,
                       TicketMessageFormatter ticketFormatter) {
        super(token);
        this.username = username;
        this.conn = conn;
        this.ticketService = ticketService;
        this.ticketFormatter = ticketFormatter;
        registerDefaultCommands();
    }

    /**
     * Initialises the default command handlers supported by the bot instance.
     */
    private void registerDefaultCommands() {
        if (ticketService != null && ticketFormatter != null) {
            commands.put("/ticket", new TicketCommandHandler(ticketService, ticketFormatter));
        }
        commands.put("/menu", new MenuCommandHandler(commands));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getBotUsername() {
        return username;
    }

    /**
     * Routes incoming Telegram updates to the appropriate command or conversational handler.
     *
     * @param update the Telegram update to process
     */
    @Override
    public void onUpdateReceived(Update update) {
        if (update == null || !update.hasMessage() || !update.getMessage().hasText()) {
            return;
        }

        long chatId = update.getMessage().getChatId();
        String text = update.getMessage().getText().trim();

        long userId;
        try {
            userId = ensureUser(update);
        } catch (SQLException ex) {
            sendMessage(chatId, "[Error] Failed to resolve user: " + ex.getMessage());
            return;
        }

        if (text.startsWith("/")) {
            handleCommand(update, userId);
            return;
        }

        if (ticketService != null && ticketService.hasActiveDraft(chatId, userId)) {
            handleTicketDraftMessage(chatId, userId, text);
            return;
        }

        handleGeneralMessage(update, userId);
    }

    /**
     * Resolves and executes a command registered with the bot.
     *
     * @param update the update that triggered the command
     * @param userId the resolved internal user identifier
     */
    private void handleCommand(Update update, long userId) {
        String commandText = update.getMessage().getText().trim();
        String commandKey = commandText.split("\\s+", 2)[0].toLowerCase(Locale.ROOT);
        CommandHandler handler = commands.get(commandKey);
        long chatId = update.getMessage().getChatId();

        if (handler == null) {
            sendMessage(chatId, "Unknown command: " + commandKey);
            return;
        }

        handler.handle(update, this, userId);
    }

    /**
     * Processes user input collected as part of an active ticket draft workflow.
     *
     * @param chatId      the chat in which the workflow is running
     * @param userId      the internal user identifier
     * @param messageText the latest message supplied by the user
     */
    private void handleTicketDraftMessage(long chatId, long userId, String messageText) {
        Optional<TicketDraft.Step> currentStep = ticketService.getActiveStep(chatId, userId);
        if (currentStep.isEmpty()) {
            sendMessage(chatId, ticketFormatter.formatError("No active ticket step found."));
            return;
        }

        try {
            Ticket ticket = ticketService.collectTicketField(chatId, userId, messageText);
            sendMessage(chatId, ticketFormatter.formatStepAcknowledgement(currentStep.get(), ticket));

            Optional<TicketDraft.Step> nextStep = ticketService.getActiveStep(chatId, userId);
            if (nextStep.isPresent()) {
                sendMessage(chatId, ticketFormatter.formatNextStepPrompt(nextStep.get()));
            } else {
                sendMessage(chatId, ticketFormatter.formatCreationComplete(ticket));
            }
        } catch (IllegalArgumentException | IllegalStateException ex) {
            sendMessage(chatId, ticketFormatter.formatError(ex.getMessage()));
        }
    }

    /**
     * Handles non-command text sent to the bot by relaying it to a language model and persisting the exchange.
     *
     * @param update the received Telegram update
     * @param userId the resolved internal user identifier
     */
    private void handleGeneralMessage(Update update, long userId) {
        String userText = update.getMessage().getText();
        long chatId = update.getMessage().getChatId();
        long telegramId = update.getMessage().getFrom().getId();

        try {
            String replyText = callChatGPT(userText);

            if (conn != null) {
                PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO messages (user_id, chat_id, text, reply) VALUES (?,?,?,?)");
                ps.setLong(1, userId);
                ps.setLong(2, chatId);
                ps.setString(3, userText);
                ps.setString(4, replyText);
                ps.executeUpdate();
                ps.close();
            }

            sendMessage(chatId, replyText);
        } catch (Exception e) {
            sendMessage(chatId, "[Error] Failed to process message: " + e.getMessage());
        }
    }

    /**
     * Ensures that the Telegram user has a corresponding database record, creating one if necessary.
     *
     * @param update the update containing the Telegram user metadata
     * @return the internal user identifier to use for persistence
     * @throws SQLException if a database operation fails
     */
    private long ensureUser(Update update) throws SQLException {
        if (conn == null) {
            return update.getMessage().getFrom().getId();
        }

        long telegramId = update.getMessage().getFrom().getId();
        PreparedStatement findUser = conn.prepareStatement(
                "SELECT id FROM users WHERE telegram_id=?");
        findUser.setLong(1, telegramId);
        ResultSet rs = findUser.executeQuery();

        if (rs.next()) {
            long userId = rs.getLong("id");
            rs.close();
            findUser.close();
            return userId;
        }

        rs.close();
        findUser.close();

        PreparedStatement insertUser = conn.prepareStatement(
                "INSERT INTO users (telegram_id, username, first_name, last_name) " +
                        "VALUES (?,?,?,?) RETURNING id");
        insertUser.setLong(1, telegramId);
        insertUser.setString(2, update.getMessage().getFrom().getUserName());
        insertUser.setString(3, update.getMessage().getFrom().getFirstName());
        insertUser.setString(4, update.getMessage().getFrom().getLastName());
        ResultSet newUser = insertUser.executeQuery();
        newUser.next();
        long userId = newUser.getLong("id");
        newUser.close();
        insertUser.close();
        return userId;
    }

    /**
     * Sends a plain text message to a Telegram chat, ignoring failures.
     *
     * @param chatId the target chat identifier
     * @param text   message body to send
     */
    public void sendMessage(long chatId, String text) {
        SendMessage message = new SendMessage(Long.toString(chatId), text);
        try {
            execute(message);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    /**
     * Calls the configured OpenAI chat completion endpoint with the supplied prompt.
     *
     * @param prompt the message to forward to the language model
     * @return the raw response payload returned by the API
     * @throws Exception if the HTTP request fails or the client cannot be created
     */
    private String callChatGPT(String prompt) throws Exception {
        HttpClient client = HttpClient.newHttpClient();
        String body = """
        {
          "model": "gpt-4o-mini",
          "messages": [{"role":"user","content":"%s"}]
        }
        """.formatted(prompt);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://api.openai.com/v1/chat/completions"))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + System.getenv("OPENAI_API_KEY"))
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        return response.body();
    }
}
