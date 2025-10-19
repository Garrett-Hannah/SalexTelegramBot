package com.salex.telegram.Bot;

import com.salex.telegram.AiPackage.ChatCompletionClient;
import com.salex.telegram.AiPackage.ConversationContextService;
import com.salex.telegram.AiPackage.OpenAIChatCompletionClient;
import com.salex.telegram.Database.ConnectionProvider;
import com.salex.telegram.Database.StaticConnectionProvider;
import com.salex.telegram.Messaging.JdbcMessageRepository;
import com.salex.telegram.Messaging.MessageRepository;
import com.salex.telegram.Messaging.NoopMessageRepository;
import com.salex.telegram.Transcription.OpenAIWhisperClient;
import com.salex.telegram.Transcription.TelegramAudioDownloader;
import com.salex.telegram.Transcription.TranscriptionService;
import com.salex.telegram.Transcription.commands.TranscriptionMessageFormatter;
import com.salex.telegram.Users.InMemoryUserService;
import com.salex.telegram.Users.JdbcUserService;
import com.salex.telegram.Users.UserService;
import com.salex.telegram.Ticketing.InMemory.InMemoryTicketRepository;
import com.salex.telegram.Ticketing.InMemory.InMemoryTicketSessionManager;
import com.salex.telegram.Ticketing.OnServer.ServerTicketRepository;
import com.salex.telegram.Ticketing.OnServer.ServerTicketSessionManager;
import com.salex.telegram.Ticketing.TicketService;
import com.salex.telegram.Ticketing.commands.TicketMessageFormatter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.objects.Update;

import java.net.http.HttpClient;
import java.sql.Connection;
import java.util.Map;
import java.util.Optional;

/**
 * Telegram bot implementation that orchestrates modules and delegates conversational fallbacks to OpenAI.
 */
public class SalexTelegramBot extends TelegramLongPollingBot {
    private static final Logger log = LoggerFactory.getLogger(SalexTelegramBot.class);

    private final String username;
    private final ConnectionProvider connectionProvider;
    private final ModuleRegistry moduleRegistry;
    private final Map<String, CommandHandler> commands;
    private final TelegramSender telegramSender;
    private final UpdateRouter updateRouter;
    private final UserService userService;

    /**
     * Convenience constructor that accepts a bare JDBC connection.
     */
    public SalexTelegramBot(String token, String username, Connection connection) {
        this(token, username, connection != null ? new StaticConnectionProvider(connection) : null);
    }

    /**
     * Creates a bot that may transparently refresh its JDBC connection when provided.
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

    /**
     * Fully-customisable constructor used by the builder.
     */
    public SalexTelegramBot(String token,
                            String username,
                            ConnectionProvider connectionProvider,
                            TicketService ticketService,
                            TicketMessageFormatter ticketFormatter,
                            TranscriptionService transcriptionService,
                            TranscriptionMessageFormatter transcriptionFormatter,
                            MessageRepository messageRepository,
                            ChatCompletionClient chatCompletionClient,
                            UserService userService) {
        this(token, username, connectionProvider, ticketService, ticketFormatter,
                transcriptionService, transcriptionFormatter, messageRepository,
                chatCompletionClient, userService, true, true, true);
    }

    SalexTelegramBot(String token,
                     String username,
                     ConnectionProvider connectionProvider,
                     TicketService ticketService,
                     TicketMessageFormatter ticketFormatter,
                     TranscriptionService transcriptionService,
                     TranscriptionMessageFormatter transcriptionFormatter,
                     MessageRepository messageRepository,
                     ChatCompletionClient chatCompletionClient,
                     UserService userService,
                     boolean ticketingEnabled,
                     boolean transcriptionEnabled,
                     boolean messageLoggingEnabled) {
        super(token);
        this.username = username;
        this.connectionProvider = connectionProvider;

        TicketService resolvedTicketService = ticketingEnabled
                ? (ticketService != null ? ticketService : createDefaultTicketService(connectionProvider))
                : null;
        TicketMessageFormatter resolvedTicketFormatter = ticketingEnabled
                ? resolveTicketFormatter(ticketFormatter, resolvedTicketService)
                : null;

        MessageRepository resolvedMessageRepository;
        if (messageLoggingEnabled) {
            resolvedMessageRepository = messageRepository != null
                    ? messageRepository
                    : createDefaultMessageRepository(connectionProvider);
        } else {
            resolvedMessageRepository = new NoopMessageRepository();
        }

        TranscriptionService resolvedTranscription = transcriptionEnabled
                ? (transcriptionService != null ? transcriptionService : createDefaultTranscriptionService())
                : null;
        TranscriptionMessageFormatter resolvedTranscriptionFormatter = transcriptionEnabled
                ? resolveTranscriptionFormatter(transcriptionFormatter, resolvedTranscription)
                : null;

        ChatCompletionClient resolvedChatClient = chatCompletionClient != null
                ? chatCompletionClient
                : createDefaultChatCompletionClient();

        this.userService = userService != null
                ? userService
                : createDefaultUserService(connectionProvider);

        ConversationContextService conversationContextService = new ConversationContextService(resolvedMessageRepository);
        ModuleBootstrapperResult bootstrapperResult = new ModuleBootstrapper().bootstrap(
                resolvedTicketService,
                resolvedTicketFormatter,
                resolvedTranscription,
                resolvedTranscriptionFormatter,
                resolvedMessageRepository,
                conversationContextService,
                resolvedChatClient
        );

        this.moduleRegistry = bootstrapperResult.moduleRegistry();
        this.commands = bootstrapperResult.commands();
        this.telegramSender = new TelegramSender(this);
        this.updateRouter = new UpdateRouter(moduleRegistry, commands, this.userService, telegramSender);

        log.info("Initialised {} modules", moduleRegistry.size());
        log.info("TelegramBot registered {} command handlers", commands.size());
    }

    private static TicketMessageFormatter resolveTicketFormatter(TicketMessageFormatter ticketFormatter,
                                                                 TicketService ticketService) {
        if (ticketService == null) {
            return ticketFormatter;
        }
        return ticketFormatter != null ? ticketFormatter : new TicketMessageFormatter();
    }

    private static TranscriptionMessageFormatter resolveTranscriptionFormatter(TranscriptionMessageFormatter transcriptionFormatter,
                                                                               TranscriptionService transcriptionService) {
        if (transcriptionService == null) {
            return transcriptionFormatter;
        }
        return transcriptionFormatter != null ? transcriptionFormatter : new TranscriptionMessageFormatter();
    }

    private static TicketService createDefaultTicketService(ConnectionProvider connectionProvider) {
        if (connectionProvider != null) {
            return new TicketService(new ServerTicketRepository(connectionProvider), new ServerTicketSessionManager(connectionProvider));
        }
        return new TicketService(new InMemoryTicketRepository(), new InMemoryTicketSessionManager());
    }

    private static MessageRepository createDefaultMessageRepository(ConnectionProvider connectionProvider) {
        if (connectionProvider != null) {
            return new JdbcMessageRepository(connectionProvider);
        }
        return new NoopMessageRepository();
    }

    private static UserService createDefaultUserService(ConnectionProvider connectionProvider) {
        if (connectionProvider != null) {
            return new JdbcUserService(connectionProvider);
        }
        return new InMemoryUserService();
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
     * Checks whether the bot has an active module of the requested type.
     */
    public boolean hasModule(Class<? extends TelegramBotModule> moduleType) {
        return moduleRegistry.contains(moduleType);
    }

    /**
     * Retrieves a module instance by its concrete type.
     */
    public <T extends TelegramBotModule> Optional<T> getModule(Class<T> moduleType) {
        return moduleRegistry.get(moduleType);
    }

    public ModuleRegistry getModuleRegistry() {
        return moduleRegistry;
    }

    @Override
    public String getBotUsername() {
        return username;
    }

    @Override
    public void onUpdateReceived(Update update) {
        updateRouter.route(update, this);
    }

    public void sendMessage(long chatId, String text) {
        sendMessage(chatId, null, text);
    }

    public void sendMessage(long chatId, Integer threadId, String text) {
        telegramSender.sendMessage(chatId, threadId, text);
    }

    public void sendChatTyping(long chatId, Integer threadId) {
        telegramSender.sendChatTyping(chatId, threadId);
    }

    public void sendChatTyping(long chatId) {
        sendChatTyping(chatId, null);
    }

    public static SalexTelegramBotBuilder builder() {
        return new SalexTelegramBotBuilder();
    }
}
