package com.salex.telegram.Bot;

import com.salex.telegram.AiPackage.ChatCompletionClient;
import com.salex.telegram.Database.ConnectionProvider;
import com.salex.telegram.Database.StaticConnectionProvider;
import com.salex.telegram.Messaging.MessageRepository;
import com.salex.telegram.Users.UserService;
import com.salex.telegram.Ticketing.TicketService;
import com.salex.telegram.Ticketing.commands.TicketMessageFormatter;
import com.salex.telegram.Transcription.TranscriptionService;
import com.salex.telegram.Transcription.commands.TranscriptionMessageFormatter;

import java.sql.Connection;

/**
 * Fluent builder used to configure {@link SalexTelegramBot} dependencies.
 */
public final class SalexTelegramBotBuilder {
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

    private boolean ticketingEnabled = true;
    private boolean transcriptionEnabled = true;
    private boolean messageLoggingEnabled = true;

    SalexTelegramBotBuilder() {
    }

    public SalexTelegramBotBuilder token(String token) {
        this.token = token;
        return this;
    }

    public SalexTelegramBotBuilder username(String username) {
        this.username = username;
        return this;
    }

    public SalexTelegramBotBuilder connection(Connection connection) {
        this.connectionProvider = connection != null ? new StaticConnectionProvider(connection) : null;
        return this;
    }

    public SalexTelegramBotBuilder connectionProvider(ConnectionProvider connectionProvider) {
        this.connectionProvider = connectionProvider;
        return this;
    }

    public SalexTelegramBotBuilder ticketService(TicketService ticketService) {
        this.ticketService = ticketService;
        this.ticketServiceSet = true;
        return this;
    }

    public SalexTelegramBotBuilder ticketFormatter(TicketMessageFormatter ticketFormatter) {
        this.ticketFormatter = ticketFormatter;
        this.ticketFormatterSet = true;
        return this;
    }

    public SalexTelegramBotBuilder transcriptionService(TranscriptionService transcriptionService) {
        this.transcriptionService = transcriptionService;
        this.transcriptionServiceSet = true;
        return this;
    }

    public SalexTelegramBotBuilder transcriptionFormatter(TranscriptionMessageFormatter transcriptionFormatter) {
        this.transcriptionFormatter = transcriptionFormatter;
        this.transcriptionFormatterSet = true;
        return this;
    }

    public SalexTelegramBotBuilder messageRepository(MessageRepository messageRepository) {
        this.messageRepository = messageRepository;
        this.messageRepositorySet = true;
        return this;
    }

    public SalexTelegramBotBuilder chatCompletionClient(ChatCompletionClient chatCompletionClient) {
        this.chatCompletionClient = chatCompletionClient;
        this.chatCompletionClientSet = true;
        return this;
    }

    public SalexTelegramBotBuilder userService(UserService userService) {
        this.userService = userService;
        this.userServiceSet = true;
        return this;
    }

    public SalexTelegramBotBuilder disableTicketing() {
        this.ticketingEnabled = false;
        this.ticketService = null;
        this.ticketServiceSet = true;
        this.ticketFormatter = null;
        this.ticketFormatterSet = true;
        return this;
    }

    public SalexTelegramBotBuilder disableTranscription() {
        this.transcriptionEnabled = false;
        this.transcriptionService = null;
        this.transcriptionServiceSet = true;
        this.transcriptionFormatter = null;
        this.transcriptionFormatterSet = true;
        return this;
    }

    public SalexTelegramBotBuilder disableMessageLogging() {
        this.messageLoggingEnabled = false;
        return this;
    }

    public SalexTelegramBot build() {
        if (token == null || token.isBlank()) {
            throw new IllegalStateException("Bot token must be provided");
        }
        if (username == null || username.isBlank()) {
            throw new IllegalStateException("Bot username must be provided");
        }

        TicketService resolvedTicketService = ticketingEnabled
                ? (ticketServiceSet ? ticketService : null)
                : null;
        TicketMessageFormatter resolvedTicketFormatter = ticketingEnabled
                ? (ticketFormatterSet ? ticketFormatter : null)
                : null;

        TranscriptionService resolvedTranscriptionService = transcriptionEnabled
                ? (transcriptionServiceSet ? transcriptionService : null)
                : null;
        TranscriptionMessageFormatter resolvedTranscriptionFormatter = transcriptionEnabled
                ? (transcriptionFormatterSet ? transcriptionFormatter : null)
                : null;

        MessageRepository resolvedMessageRepository = messageLoggingEnabled
                ? (messageRepositorySet ? messageRepository : null)
                : null;
        ChatCompletionClient resolvedChatClient = chatCompletionClientSet ? chatCompletionClient : null;
        UserService resolvedUserService = userServiceSet ? userService : null;

        return new SalexTelegramBot(
                token,
                username,
                connectionProvider,
                resolvedTicketService,
                resolvedTicketFormatter,
                resolvedTranscriptionService,
                resolvedTranscriptionFormatter,
                resolvedMessageRepository,
                resolvedChatClient,
                resolvedUserService,
                ticketingEnabled,
                transcriptionEnabled,
                messageLoggingEnabled
        );
    }
}
