package com.salex.telegram.Bot;

import com.salex.telegram.Transcription.OpenAIWhisperClient;
import com.salex.telegram.Transcription.TelegramAudioDownloader;
import com.salex.telegram.Transcription.TranscriptionException;
import com.salex.telegram.Transcription.TranscriptionResult;
import com.salex.telegram.Transcription.TranscriptionService;
import com.salex.telegram.Transcription.commands.TranscriptionCommandHandler;
import com.salex.telegram.Transcription.commands.TranscriptionMessageFormatter;
import com.salex.telegram.commanding.CommandHandler;
import com.salex.telegram.ticketing.InMemory.InMemoryTicketRepository;
import com.salex.telegram.ticketing.InMemory.InMemoryTicketSessionManager;
import com.salex.telegram.ticketing.OnServer.ServerTicketRepository;
import com.salex.telegram.ticketing.OnServer.ServerTicketSessionManager;
import com.salex.telegram.ticketing.Ticket;
import com.salex.telegram.ticketing.TicketDraft;
import com.salex.telegram.ticketing.TicketService;
import com.salex.telegram.ticketing.commands.TicketCommandHandler;
import com.salex.telegram.ticketing.commands.TicketMessageFormatter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Message;
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
    private static final Logger log = LoggerFactory.getLogger(TelegramBot.class);

    private final String username;
    private final Connection conn;
    private final TicketService ticketService;
    private final TicketMessageFormatter ticketFormatter;
    private final TranscriptionService transcriptionService;
    private final TranscriptionMessageFormatter transcriptionFormatter;
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
                createDefaultTicketService(conn),
                new TicketMessageFormatter(),
                null,
                null);
        log.info("TelegramBot initialised with {} ticket backend", conn != null ? "JDBC" : "in-memory");
    }

    /**
     * Creates a bot with explicit ticket service and formatter dependencies.
     *
     * @param token                   bot token provided by Telegram
     * @param username                bot public username
     * @param conn                    JDBC connection used for persistence (may be {@code null})
     * @param ticketService           service handling ticket lifecycle operations (may be {@code null} for disabled ticketing)
     * @param ticketFormatter         formatter used for rendering ticket messages (may be {@code null} for disabled ticketing)
     * @param transcriptionService    service used for transcribing audio messages (may be {@code null} to disable feature)
     * @param transcriptionFormatter  formatter used for transcription responses (ignored when service is {@code null})
     */
    public TelegramBot(String token, String username, Connection conn,
                       TicketService ticketService,
                       TicketMessageFormatter ticketFormatter,
                       TranscriptionService transcriptionService,
                       TranscriptionMessageFormatter transcriptionFormatter) {
        super(token);
        this.username = username;
        this.conn = conn;
        this.ticketService = ticketService;
        this.ticketFormatter = ticketFormatter;
        TranscriptionService resolvedTranscription = transcriptionService != null
                ? transcriptionService
                : createDefaultTranscriptionService();
        this.transcriptionService = resolvedTranscription;
        this.transcriptionFormatter = resolvedTranscription != null
                ? (transcriptionFormatter != null ? transcriptionFormatter : new TranscriptionMessageFormatter())
                : null;
        registerDefaultCommands();
        log.info("TelegramBot registered {} command handlers", commands.size());
    }

    private static TicketService createDefaultTicketService(Connection conn) {
        if (conn != null) {
            return new TicketService(new ServerTicketRepository(conn), new ServerTicketSessionManager(conn));
        }
        return new TicketService(new InMemoryTicketRepository(), new InMemoryTicketSessionManager());
    }

    private TranscriptionService createDefaultTranscriptionService() {
        String apiKey = System.getenv("OPENAI_API_KEY");
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("OPENAI_API_KEY not provided; transcription features disabled.");
            return null;
        }
        HttpClient client = HttpClient.newHttpClient();
        OpenAIWhisperClient whisperClient = new OpenAIWhisperClient(client, apiKey, System.getenv("WHISPER_MODEL"));
        return new TranscriptionService(new TelegramAudioDownloader(this), whisperClient);
    }

    /**
     * Initialises the default command handlers supported by the bot instance.
     */
    private void registerDefaultCommands() {
        if (ticketService != null && ticketFormatter != null) {
            commands.put("/ticket", new TicketCommandHandler(ticketService, ticketFormatter));
        }
        if (transcriptionService != null && transcriptionFormatter != null) {
            commands.put("/transcribe", new TranscriptionCommandHandler(transcriptionService, transcriptionFormatter));
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
        if (update == null || !update.hasMessage()) {
            log.debug("Ignored update without message content");
            return;
        }

        Message message = update.getMessage();
        long chatId = message.getChatId();
        long telegramUserId = message.getFrom() != null ? message.getFrom().getId() : -1L;
        String text = message.hasText() ? message.getText().trim() : "";
        boolean hasAudio = hasAudioContent(message);

        if (text.isEmpty() && !hasAudio) {
            log.debug("Ignored update without text or audio content in chat {}", chatId);
            return;
        }

        log.info("Received update{} in chat {} from user {}",
                text.isEmpty() ? "" : (" \"" + text + "\""),
                chatId,
                telegramUserId);

        long userId;
        try {
            userId = ensureUser(update);
        } catch (SQLException ex) {
            sendMessage(chatId, "[Error] Failed to resolve user: " + ex.getMessage());
            log.error("Failed to resolve user for chat {}: {}", chatId, ex.getMessage(), ex);
            return;
        }

        if (!text.isEmpty() && text.startsWith("/")) {
            log.debug("Dispatching command {}", text);
            handleCommand(update, userId);
            return;
        }

        if (hasAudio) {
            log.debug("Handling audio message for user {}", userId);
            handleAudioMessage(message, chatId, userId);
            return;
        }

        if (ticketService != null && ticketService.hasActiveDraft(chatId, userId)) {
            log.debug("Routing message to active ticket draft for user {}", userId);
            handleTicketDraftMessage(chatId, userId, text);
            return;
        }

        log.debug("Handling general message for user {}", userId);
        handleGeneralMessage(update, userId);
    }

    private boolean hasAudioContent(Message message) {
        return message != null && (message.hasVoice() || message.hasAudio() || message.hasVideoNote());
    }

    private void handleAudioMessage(Message message, long chatId, long userId) {
        if (transcriptionService == null || transcriptionFormatter == null) {
            log.info("Transcription requested by user {} but service is disabled", userId);
            sendMessage(chatId, "Audio transcription is currently unavailable.");
            return;
        }

        try {
            TranscriptionResult result = transcriptionService.transcribe(message);
            sendMessage(chatId, transcriptionFormatter.formatResult(result));
        } catch (TranscriptionException ex) {
            log.error("Failed to transcribe audio for user {}: {}", userId, ex.getMessage(), ex);
            sendMessage(chatId, transcriptionFormatter.formatError(ex.getMessage()));
        }
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
            log.warn("User {} invoked unknown command {}", userId, commandKey);
            return;
        }

        log.info("Executing command {} for user {}", handler.getName(), userId);
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
            log.warn("No active ticket step found for chat {}, user {}", chatId, userId);
            return;
        }

        try {
            Ticket ticket = ticketService.collectTicketField(chatId, userId, messageText);
            log.info("Collected ticket field at step {} for ticket {}", currentStep.get(), ticket.getId());
            sendMessage(chatId, ticketFormatter.formatStepAcknowledgement(currentStep.get(), ticket));

            Optional<TicketDraft.Step> nextStep = ticketService.getActiveStep(chatId, userId);
            if (nextStep.isPresent()) {
                log.debug("Next ticket step for ticket {} is {}", ticket.getId(), nextStep.get());
                sendMessage(chatId, ticketFormatter.formatNextStepPrompt(nextStep.get()));
            } else {
                log.info("Ticket {} creation complete", ticket.getId());
                sendMessage(chatId, ticketFormatter.formatCreationComplete(ticket));
            }
        } catch (IllegalArgumentException | IllegalStateException ex) {
            sendMessage(chatId, ticketFormatter.formatError(ex.getMessage()));
            log.error("Failed to collect ticket field for chat {}, user {}: {}", chatId, userId, ex.getMessage(), ex);
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
            log.info("ChatGPT responded to user {} with {} characters", userId, replyText.length());

            if (conn != null) {
                PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO messages (user_id, chat_id, text, reply) VALUES (?,?,?,?)");
                ps.setLong(1, userId);
                ps.setLong(2, chatId);
                ps.setString(3, userText);
                ps.setString(4, replyText);
                ps.executeUpdate();
                ps.close();
                log.debug("Persisted message for user {} in chat {}", userId, chatId);
            }

            sendMessage(chatId, replyText);
        } catch (Exception e) {
            sendMessage(chatId, "[Error] Failed to process message: " + e.getMessage());
            log.error("Failed to handle general message for user {}: {}", userId, e.getMessage(), e);
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
            log.debug("Database connection unavailable; using telegram ID as user ID");
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
            log.debug("Resolved existing user {} for telegram {}", userId, telegramId);
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
        log.info("Created new user {} for telegram {}", userId, telegramId);
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
            log.error("Failed to send message to chat {}: {}", chatId, e.getMessage(), e);
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
        log.debug("ChatGPT API responded with status {}", response.statusCode());
        return response.body();
    }
}
