package com.salex.telegram.Bot;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.salex.telegram.AiPackage.ConversationContextService;
import com.salex.telegram.AiPackage.ConversationMessage;
import com.salex.telegram.Database.ConnectionProvider;
import com.salex.telegram.Database.StaticConnectionProvider;
import com.salex.telegram.Transcription.OpenAIWhisperClient;
import com.salex.telegram.Transcription.TelegramAudioDownloader;
import com.salex.telegram.Transcription.TranscriptionService;
import com.salex.telegram.Transcription.TranscriptionBotModule;
import com.salex.telegram.Transcription.commands.TranscriptionMessageFormatter;
import com.salex.telegram.Messaging.JdbcMessageRepository;
import com.salex.telegram.Messaging.MessageRepository;
import com.salex.telegram.Messaging.NoopMessageRepository;
import com.salex.telegram.Ticketing.InMemory.InMemoryTicketRepository;
import com.salex.telegram.Ticketing.InMemory.InMemoryTicketSessionManager;
import com.salex.telegram.Ticketing.OnServer.ServerTicketRepository;
import com.salex.telegram.Ticketing.OnServer.ServerTicketSessionManager;
import com.salex.telegram.Ticketing.TicketService;
import com.salex.telegram.Ticketing.TicketingBotModule;
import com.salex.telegram.Ticketing.commands.TicketMessageFormatter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.ActionType;
import org.telegram.telegrambots.meta.api.methods.send.SendChatAction;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Telegram bot implementation that dispatches commands, manages ticket workflows,
 * and forwards free-form messages to a language model while keeping short-term conversation context.
 */
public class SalexTelegramBot extends TelegramLongPollingBot {
    private static final Logger log = LoggerFactory.getLogger(SalexTelegramBot.class);
    private final String username;
    private final ConnectionProvider connectionProvider;
    private final TicketService ticketService;
    private final TicketMessageFormatter ticketFormatter;
    private final TranscriptionService transcriptionService;
    private final TranscriptionMessageFormatter transcriptionFormatter;
    private final MessageRepository messageRepository;
    private final ConversationContextService conversationContextService;
    private final List<TelegramBotModule> modules;
    private final Map<String, CommandHandler> commands = new HashMap<>();

    /**
     * Creates a bot that uses in-memory ticket backing services for lightweight deployments.
     *
     * @param token    bot token provided by Telegram
     * @param username bot public username
     * @param conn     JDBC connection used for persistence (may be {@code null} for in-memory usage)
     */
    public SalexTelegramBot(String token, String username, Connection conn) {
        this(token, username, conn != null ? new StaticConnectionProvider(conn) : null);
    }

    /**
     * Creates a bot that may transparently refresh its JDBC connection when provided.
     *
     * @param token               bot token provided by Telegram
     * @param username            bot public username
     * @param connectionProvider  provider used to supply JDBC connections (may be {@code null})
     */
    public SalexTelegramBot(String token, String username, ConnectionProvider connectionProvider) {
        this(token, username, connectionProvider,
                createDefaultTicketService(connectionProvider),
                new TicketMessageFormatter(),
                null,
                null,
                null);
        log.info("TelegramBot initialised with {} ticket backend", connectionProvider != null ? "JDBC" : "in-memory");
    }

    /**
     * Creates a bot with explicit ticket service and formatter dependencies.
     *
     * @param token                   bot token provided by Telegram
     * @param username                bot public username
     * @param connectionProvider      provider used for persistence (may be {@code null})
     * @param ticketService           service handling ticket lifecycle operations (may be {@code null} for disabled ticketing)
     * @param ticketFormatter         formatter used for rendering ticket messages (may be {@code null} for disabled ticketing)
     * @param transcriptionService    service used for transcribing audio messages (may be {@code null} to disable feature)
     * @param transcriptionFormatter  formatter used for transcription responses (ignored when service is {@code null})
     * @param messageRepository       repository used for persisting and retrieving conversational turns (may be {@code null} to auto-configure)
     */
    public SalexTelegramBot(String token, String username, ConnectionProvider connectionProvider,
                            TicketService ticketService,
                            TicketMessageFormatter ticketFormatter,
                            TranscriptionService transcriptionService,
                            TranscriptionMessageFormatter transcriptionFormatter,
                            MessageRepository messageRepository) {
        super(token);
        this.username = username;
        this.connectionProvider = connectionProvider;
        this.ticketService = ticketService;
        this.ticketFormatter = ticketFormatter;
        this.messageRepository = messageRepository != null
                ? messageRepository
                : createDefaultMessageRepository(connectionProvider);
        TranscriptionService resolvedTranscription = transcriptionService != null
                ? transcriptionService
                : createDefaultTranscriptionService();
        this.transcriptionService = resolvedTranscription;
        this.transcriptionFormatter = resolvedTranscription != null
                ? (transcriptionFormatter != null ? transcriptionFormatter : new TranscriptionMessageFormatter())
                : null;

        this.conversationContextService = new ConversationContextService(this.messageRepository);
        this.modules = initialiseModules(ticketService, ticketFormatter, resolvedTranscription, this.transcriptionFormatter);
        log.info("Initialised {} modules", modules.size());
        registerModuleCommands();

        log.info("TelegramBot registered {} command handlers", commands.size());
        //TODO: set up modules so that itll also note how many modules there are.
    }

    private static TicketService createDefaultTicketService(ConnectionProvider connectionProvider) {
        if (connectionProvider != null) {
            return new TicketService(new ServerTicketRepository(connectionProvider), new ServerTicketSessionManager(connectionProvider));
        }
        return new TicketService(new InMemoryTicketRepository(), new InMemoryTicketSessionManager());
    }

    private MessageRepository createDefaultMessageRepository(ConnectionProvider connectionProvider) {
        if (connectionProvider != null) {
            return new JdbcMessageRepository(connectionProvider);
        }
        return new NoopMessageRepository();
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
     * Creates the ordered list of modules that can contribute commands and consume updates.
     * Ticketing and transcription modules are included only when their backing services are available.
     */
    private List<TelegramBotModule> initialiseModules(TicketService ticketService,
                                                      TicketMessageFormatter ticketFormatter,
                                                      TranscriptionService transcriptionService,
                                                      TranscriptionMessageFormatter transcriptionFormatter) {
        List<TelegramBotModule> moduleList = new ArrayList<>();
        if (ticketService != null && ticketFormatter != null) {
            moduleList.add(new TicketingBotModule(ticketService, ticketFormatter));
        }
        if (transcriptionService != null && transcriptionFormatter != null) {
            moduleList.add(new TranscriptionBotModule(transcriptionService, transcriptionFormatter));
        }
        moduleList.add(new GenericBotModule(messageRepository, conversationContextService));
        return List.copyOf(moduleList);
    }

    /**
     * Registers commands contributed by each module and appends the shared `/menu` command.
     */
    private void registerModuleCommands() {
        commands.clear();
        for (TelegramBotModule module : modules) {
            module.getCommands().forEach((name, handler) -> {
                String key = name.toLowerCase(Locale.ROOT);
                CommandHandler previous = commands.put(key, handler);
                if (previous != null && previous != handler) {
                    log.warn("Command {} supplied by {} overrides {}", key,
                            module.getClass().getSimpleName(), previous.getClass().getSimpleName());
                }
            });
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

    //TODO: question, would it be beneficial to make this also into its own almost class. Should the bot just be like 30 different things that
    //Essetnailly just orhcestrates the interaction? This is for another time.
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
        Integer threadId = message.getMessageThreadId();
        String text = message.hasText() ? message.getText().trim() : "";

        log.info("Received update{} in chat {} from user {}",
                text.isEmpty() ? "" : (" \"" + text + "\""),
                chatId,
                telegramUserId);

        long userId;
        try {
            userId = ensureUser(update);
        } catch (SQLException ex) {
            sendMessage(chatId, threadId, "[Error] Failed to resolve user: " + ex.getMessage());
            log.error("Failed to resolve user for chat {}: {}", chatId, ex.getMessage(), ex);
            return;
        }

        if (!text.isEmpty() && text.startsWith("/")) {
            log.debug("Dispatching command {}", text);
            handleCommand(update, userId);
            return;
        }

        for (TelegramBotModule module : modules) {
            if (module.canHandle(update, userId)) {
                log.debug("Routing update in chat {} to module {}", chatId, module.getClass().getSimpleName());
                module.handle(update, this, userId);
                return;
            }
        }

        log.debug("No module accepted update in chat {} from user {}", chatId, userId);
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
        Integer threadId = update.getMessage().getMessageThreadId();

        if (handler == null) {
            sendMessage(chatId, threadId, "Unknown command: " + commandKey);
            log.warn("User {} invoked unknown command {}", userId, commandKey);
            return;
        }

        log.info("Executing command {} for user {}", handler.getName(), userId);
        handler.handle(update, this, userId);
    }

    //TODO: this likely should and could be refactored into a subclass, of course this would make some things easier.
    /**
     * Ensures that the Telegram user has a corresponding database record, creating one if necessary.
     *
     * @param update the update containing the Telegram user metadata
     * @return the internal user identifier to use for persistence
     * @throws SQLException if a database operation fails
     */
    private long ensureUser(Update update) throws SQLException {
        if (connectionProvider == null) {
            log.debug("Database connection unavailable; using telegram ID as user ID");
            return update.getMessage().getFrom().getId();
        }

        long telegramId = update.getMessage().getFrom().getId();
        Connection connection = connectionProvider.getConnection();
        try (PreparedStatement findUser = connection.prepareStatement(
                "SELECT id FROM users WHERE telegram_id=?")) {
            findUser.setLong(1, telegramId);
            try (ResultSet rs = findUser.executeQuery()) {
                if (rs.next()) {
                    long userId = rs.getLong("id");
                    log.debug("Resolved existing user {} for telegram {}", userId, telegramId);
                    return userId;
                }
            }
        }

        try (PreparedStatement insertUser = connection.prepareStatement(
                "INSERT INTO users (telegram_id, username, first_name, last_name) " +
                        "VALUES (?,?,?,?) RETURNING id")) {
            insertUser.setLong(1, telegramId);
            insertUser.setString(2, update.getMessage().getFrom().getUserName());
            insertUser.setString(3, update.getMessage().getFrom().getFirstName());
            insertUser.setString(4, update.getMessage().getFrom().getLastName());
            try (ResultSet newUser = insertUser.executeQuery()) {
                newUser.next();
                long userId = newUser.getLong("id");
                log.info("Created new user {} for telegram {}", userId, telegramId);
                return userId;
            }
        }
    }

    /**
     * Sends a plain text message to a Telegram chat, ignoring failures.
     *
     * @param chatId the target chat identifier
     * @param text   message body to send
     */
    public void sendMessage(long chatId, String text) {
        sendMessage(chatId, null, text);
    }

    public void sendMessage(long chatId, Integer threadId, String text) {
        SendMessage message = new SendMessage(Long.toString(chatId), text);
        if (threadId != null) {
            message.setMessageThreadId(threadId);
        }
        try {
            execute(message);
        } catch (TelegramApiException e) {
            e.printStackTrace();
            log.error("Failed to send message to chat {}: {}", chatId, e.getMessage(), e);
        }
    }

    public void sendChatTyping(long chatId, Integer threadId) {
        SendChatAction action = new SendChatAction();
        action.setChatId(Long.toString(chatId));
        action.setAction(ActionType.TYPING);
        if (threadId != null) {
            action.setMessageThreadId(threadId);
        }
        try {
            execute(action);
        } catch (TelegramApiException e) {
            e.printStackTrace();
            log.error("Failed to send action to chat {}: {}", chatId, e.getMessage(), e);
        }
        log.info("Outputting Typing action in chat");
    }

    public void sendChatTyping(long chatId) {
        sendChatTyping(chatId, null);
    }


    /**TODO: Change this so that chat queries are returned as a type of object.
     *
     */

    /**
     * Convenience helper that forwards a single user prompt to the chat completion endpoint.
     * This delegates to {@link #callChatGPT(List)} after wrapping the prompt in a conversation list.
     *
     * @param prompt the latest user message to forward to the language model
     * @return the assistant reply extracted from the API response
     * @throws Exception if the HTTP request fails or the client cannot be created
     */
    public String callChatGPT(String prompt) throws Exception {
        String safePrompt = prompt != null ? prompt : "";
        return callChatGPT(List.of(new ConversationMessage("user", safePrompt)));
    }

    //TODO: turn this into a largely self contained subclass. 
    /**
     * Calls the configured OpenAI chat-completions endpoint using the supplied conversation history.
     *
     * @param conversation ordered list of prior exchanges ending with the latest user message
     * @return the assistant reply extracted from the API response
     * @throws Exception if the HTTP request fails or the client cannot be created
     */
    public String callChatGPT(List<ConversationMessage> conversation) throws Exception {
        HttpClient client = HttpClient.newHttpClient();
        Objects.requireNonNull(conversation, "conversation");

        JsonObject payload = new JsonObject();
        payload.addProperty("model", "gpt-4o-mini");

        JsonArray messages = new JsonArray();
        if (conversation.isEmpty()) {
            JsonObject fallbackMessage = new JsonObject();
            fallbackMessage.addProperty("role", "user");
            fallbackMessage.addProperty("content", "");
            messages.add(fallbackMessage);
        } else {
            for (ConversationMessage entry : conversation) {
                JsonObject messageObject = new JsonObject();
                messageObject.addProperty("role", entry.role());
                messageObject.addProperty("content", entry.content());
                messages.add(messageObject);
            }
        }
        payload.add("messages", messages);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://api.openai.com/v1/chat/completions"))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + System.getenv("OPENAI_API_KEY"))
                .POST(HttpRequest.BodyPublishers.ofString(payload.toString()))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        log.debug("ChatGPT API responded with status {}", response.statusCode());

        // parse with Gson //TODO: make this into something easier to handle responses.
        JsonObject root = JsonParser.parseString(response.body()).getAsJsonObject();
        JsonArray choices = root.getAsJsonArray("choices");
        JsonObject message = choices.get(0).getAsJsonObject().getAsJsonObject("message");
        String content = message.get("content").getAsString();

        return content; // just the assistant reply
    }

    public static Builder builder() {
        return new Builder();
    }

    private SalexTelegramBot(Builder builder) {
        super(builder.token);
        this.username = builder.username;
        this.connectionProvider = builder.connectionProvider;

        TicketService resolvedTicketService;
        if (builder.ticketingEnabled) {
            if (builder.ticketServiceSet) {
                resolvedTicketService = builder.ticketService;
            } else {
                resolvedTicketService = createDefaultTicketService(connectionProvider);
            }
        } else {
            resolvedTicketService = null;
        }
        this.ticketService = resolvedTicketService;

        TicketMessageFormatter resolvedTicketFormatter;
        if (resolvedTicketService != null) {
            if (builder.ticketFormatterSet) {
                resolvedTicketFormatter = builder.ticketFormatter;
            } else {
                resolvedTicketFormatter = new TicketMessageFormatter();
            }
        } else if (builder.ticketFormatterSet) {
            resolvedTicketFormatter = builder.ticketFormatter;
        } else {
            resolvedTicketFormatter = null;
        }
        this.ticketFormatter = resolvedTicketFormatter;

        TranscriptionService resolvedTranscription;
        if (builder.transcriptionEnabled) {
            if (builder.transcriptionServiceSet) {
                resolvedTranscription = builder.transcriptionService;
            } else {
                resolvedTranscription = createDefaultTranscriptionService();
            }
        } else {
            resolvedTranscription = null;
        }
        this.transcriptionService = resolvedTranscription;
        this.transcriptionFormatter = resolvedTranscription != null
                ? (builder.transcriptionFormatterSet
                ? builder.transcriptionFormatter
                : new TranscriptionMessageFormatter())
                : null;

        MessageRepository resolvedMessageRepository;
        if (builder.messageLoggingEnabled) {
            if (builder.messageRepositorySet && builder.messageRepository != null) {
                resolvedMessageRepository = builder.messageRepository;
            } else {
                resolvedMessageRepository = createDefaultMessageRepository(connectionProvider);
            }
        } else {
            resolvedMessageRepository = new NoopMessageRepository();
        }
        this.messageRepository = resolvedMessageRepository;

        this.conversationContextService = new ConversationContextService(this.messageRepository);
        this.modules = initialiseModules(resolvedTicketService, resolvedTicketFormatter,
                resolvedTranscription, this.transcriptionFormatter);
        log.info("Initialised {} modules", modules.size());
        registerModuleCommands();
        log.info("TelegramBot registered {} command handlers", commands.size());
    }

    public static final class Builder {
        private String token;
        private String username;
        private ConnectionProvider connectionProvider;
        private TicketService ticketService;
        private TicketMessageFormatter ticketFormatter;
        private TranscriptionService transcriptionService;
        private TranscriptionMessageFormatter transcriptionFormatter;
        private MessageRepository messageRepository;

        private boolean ticketServiceSet;
        private boolean ticketFormatterSet;
        private boolean transcriptionServiceSet;
        private boolean transcriptionFormatterSet;
        private boolean messageRepositorySet;

        private boolean ticketingEnabled = true;
        private boolean transcriptionEnabled = true;
        private boolean messageLoggingEnabled = true;

        private Builder() {
        }

        public Builder token(String token) {
            this.token = token;
            return this;
        }

        public Builder username(String username) {
            this.username = username;
            return this;
        }

        public Builder connection(Connection connection) {
            this.connectionProvider = connection != null ? new StaticConnectionProvider(connection) : null;
            return this;
        }

        public Builder connectionProvider(ConnectionProvider connectionProvider) {
            this.connectionProvider = connectionProvider;
            return this;
        }

        public Builder ticketService(TicketService ticketService) {
            this.ticketService = ticketService;
            this.ticketServiceSet = true;
            return this;
        }

        public Builder ticketFormatter(TicketMessageFormatter ticketFormatter) {
            this.ticketFormatter = ticketFormatter;
            this.ticketFormatterSet = true;
            return this;
        }

        public Builder transcriptionService(TranscriptionService transcriptionService) {
            this.transcriptionService = transcriptionService;
            this.transcriptionServiceSet = true;
            return this;
        }

        public Builder transcriptionFormatter(TranscriptionMessageFormatter transcriptionFormatter) {
            this.transcriptionFormatter = transcriptionFormatter;
            this.transcriptionFormatterSet = true;
            return this;
        }

        public Builder messageRepository(MessageRepository messageRepository) {
            this.messageRepository = messageRepository;
            this.messageRepositorySet = true;
            return this;
        }

        public Builder disableMessageLogging() {
            this.messageLoggingEnabled = false;
            return this;
        }

        public Builder disableTicketing() {
            this.ticketingEnabled = false;
            this.ticketService = null;
            this.ticketServiceSet = true;
            this.ticketFormatter = null;
            this.ticketFormatterSet = true;
            return this;
        }

        public Builder disableTranscription() {
            this.transcriptionEnabled = false;
            this.transcriptionService = null;
            this.transcriptionServiceSet = true;
            this.transcriptionFormatter = null;
            this.transcriptionFormatterSet = true;
            return this;
        }

        public SalexTelegramBot build() {
            if (token == null || token.isBlank()) {
                throw new IllegalStateException("Bot token must be provided");
            }
            if (username == null || username.isBlank()) {
                throw new IllegalStateException("Bot username must be provided");
            }
            return new SalexTelegramBot(this);
        }
    }
}
