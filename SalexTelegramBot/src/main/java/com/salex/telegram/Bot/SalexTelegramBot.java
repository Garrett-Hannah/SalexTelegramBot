package com.salex.telegram.Bot;

import com.salex.telegram.AiPackage.ChatCompletionClient;
import com.salex.telegram.AiPackage.ConversationContextService;
import com.salex.telegram.AiPackage.OpenAIChatCompletionClient;
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
import com.salex.telegram.Users.InMemoryUserService;
import com.salex.telegram.Users.JdbcUserService;
import com.salex.telegram.Users.UserRecord;
import com.salex.telegram.Users.UserService;
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
import org.telegram.telegrambots.meta.api.objects.User;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.net.http.HttpClient;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Telegram bot implementation that dispatches commands, manages ticket workflows,
 * and forwards free-form messages to a language model while keeping short-term conversation context.
 */
public class SalexTelegramBot extends TelegramLongPollingBot {
    //TODO: is there something i can do with all these things? maybe.
    private static final Logger log = LoggerFactory.getLogger(SalexTelegramBot.class);
    private final String username;
    private final ConnectionProvider connectionProvider;
    private final TicketService ticketService;
    private final TicketMessageFormatter ticketFormatter;
    private final TranscriptionService transcriptionService;
    private final TranscriptionMessageFormatter transcriptionFormatter;
    private final MessageRepository messageRepository;
    private final ConversationContextService conversationContextService;
    private final ChatCompletionClient chatCompletionClient;
    private final UserService userService;
    private final ModuleRegistry moduleRegistry;
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
                null,
                null,
                null);
        log.info("TelegramBot initialised with {} ticket backend", connectionProvider != null ? "JDBC" : "in-memory");
    }

    //TODO: question can i fix this or is it nessecary to do this.
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
     * @param chatCompletionClient    client used to obtain language model responses (may be {@code null} to auto-configure)
     */
    public SalexTelegramBot(String token, String username, ConnectionProvider connectionProvider,
                            TicketService ticketService,
                            TicketMessageFormatter ticketFormatter,
                            TranscriptionService transcriptionService,
                            TranscriptionMessageFormatter transcriptionFormatter,
                            MessageRepository messageRepository,
                            ChatCompletionClient chatCompletionClient,
                            UserService userService) {
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

        this.chatCompletionClient = chatCompletionClient != null
                ? chatCompletionClient
                : createDefaultChatCompletionClient();
        this.userService = userService != null
                ? userService
                : createDefaultUserService(connectionProvider);
        this.conversationContextService = new ConversationContextService(this.messageRepository);
        this.moduleRegistry = initialiseModules(ticketService, ticketFormatter, resolvedTranscription,
                this.transcriptionFormatter, this.chatCompletionClient);
        log.info("Initialised {} modules", moduleRegistry.size());
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

    private UserService createDefaultUserService(ConnectionProvider connectionProvider) {
        if (connectionProvider != null) {
            return new JdbcUserService(connectionProvider);
        }
        return new InMemoryUserService();
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

    private ChatCompletionClient createDefaultChatCompletionClient() {
        String apiKey = System.getenv("OPENAI_API_KEY");
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("OPENAI_API_KEY not provided; conversational relay will be unavailable.");
            return conversation -> {
                throw new IllegalStateException("OPENAI_API_KEY not configured");
            };
        }
        String model = Optional.ofNullable(System.getenv("OPENAI_CHAT_MODEL"))
                .filter(value -> !value.isBlank())
                .orElse("gpt-4o-mini");
        return new OpenAIChatCompletionClient(HttpClient.newHttpClient(), apiKey, model);
    }

    //TODO: questioning whether there is a better way to access this without all the inputs. kinda a red flag.
    /**
     * Creates the ordered list of modules that can contribute commands and consume updates.
     * Ticketing and transcription modules are included only when their backing services are available.
     */
    private ModuleRegistry initialiseModules(TicketService ticketService,
                                             TicketMessageFormatter ticketFormatter,
                                             TranscriptionService transcriptionService,
                                             TranscriptionMessageFormatter transcriptionFormatter,
                                             ChatCompletionClient chatCompletionClient) {
        ModuleRegistry registry = new ModuleRegistry();
        if (ticketService != null && ticketFormatter != null) {
            registry.register(new TicketingBotModule(ticketService, ticketFormatter));
        }
        if (transcriptionService != null && transcriptionFormatter != null) {
            registry.register(new TranscriptionBotModule(transcriptionService, transcriptionFormatter));
        }
        registry.register(new ConversationalRelayModule(messageRepository, conversationContextService, chatCompletionClient));
        return registry;
    }

    /**
     * Registers commands contributed by each module and appends the shared `/menu` command.
     */
    private void registerModuleCommands() {
        commands.clear();
        moduleRegistry.forEach(module -> module.getCommands().forEach((name, handler) -> {
            String key = name.toLowerCase(Locale.ROOT);
            CommandHandler previous = commands.put(key, handler);
            if (previous != null && previous != handler) {
                log.warn("Command {} supplied by {} overrides {}", key,
                        module.getClass().getSimpleName(), previous.getClass().getSimpleName());
            }
        }));
        commands.put("/menu", new MenuCommandHandler(commands));
    }

    /**
     * Checks whether the bot has an active module of the requested type.
     */
    public boolean hasModule(Class<? extends TelegramBotModule> moduleType) {
        return moduleRegistry.contains(moduleType);
    }

    /**
     * Retrieves a module instance by its concrete type.
     *
     * @param moduleType concrete module class
     * @param <T>        module subtype
     * @return optional module instance
     */
    public <T extends TelegramBotModule> Optional<T> getModule(Class<T> moduleType) {
        return moduleRegistry.get(moduleType);
    }

    /**
     * @return immutable registry view for advanced scenarios
     */
    public ModuleRegistry getModuleRegistry() {
        return moduleRegistry;
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

        User telegramUser = message.getFrom();
        if (telegramUser == null) {
            log.warn("Received message in chat {} without sender metadata; update ignored", chatId);
            return;
        }

        long userId;
        try {
            UserRecord userRecord = userService.ensureUser(telegramUser);
            userId = userRecord.id();
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

        moduleRegistry.stream()
                .filter(module -> module.canHandle(update, userId))
                .findFirst()
                .ifPresentOrElse(module -> {
                    log.debug("Routing update in chat {} to module {}", chatId, module.getClass().getSimpleName());
                    module.handle(update, this, userId);
                }, () -> log.debug("No module accepted update in chat {} from user {}", chatId, userId));

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

        ChatCompletionClient resolvedChatClient;
        if (builder.chatCompletionClientSet && builder.chatCompletionClient != null) {
            resolvedChatClient = builder.chatCompletionClient;
        } else {
            resolvedChatClient = createDefaultChatCompletionClient();
        }
        this.chatCompletionClient = resolvedChatClient;

        UserService resolvedUserService;
        if (builder.userServiceSet && builder.userService != null) {
            resolvedUserService = builder.userService;
        } else {
            resolvedUserService = createDefaultUserService(connectionProvider);
        }
        this.userService = resolvedUserService;

        this.conversationContextService = new ConversationContextService(this.messageRepository);
        this.moduleRegistry = initialiseModules(resolvedTicketService, resolvedTicketFormatter,
                resolvedTranscription, this.transcriptionFormatter, this.chatCompletionClient);
        log.info("Initialised {} modules", moduleRegistry.size());
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
        private ChatCompletionClient chatCompletionClient;
        private UserService userService;

        private boolean ticketServiceSet;
        private boolean ticketFormatterSet;
        private boolean transcriptionServiceSet;
        private boolean transcriptionFormatterSet;
        private boolean messageRepositorySet;
        private boolean chatCompletionClientSet;
        private boolean userServiceSet;

        //Why is this a fucking thing.
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

        public Builder chatCompletionClient(ChatCompletionClient chatCompletionClient) {
            this.chatCompletionClient = chatCompletionClient;
            this.chatCompletionClientSet = true;
            return this;
        }

        public Builder userService(UserService userService) {
            this.userService = userService;
            this.userServiceSet = true;
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
