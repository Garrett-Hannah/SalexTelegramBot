package com.salex.telegram.modules.transcription;

import com.salex.telegram.bot.SalexTelegramBot;
import com.salex.telegram.modules.CommandHandler;
import com.salex.telegram.modules.MessagingHandlerService;
import com.salex.telegram.transcription.TranscriptionException;
import com.salex.telegram.transcription.TranscriptionResult;
import com.salex.telegram.transcription.TranscriptionService;
import com.salex.telegram.modules.transcription.commands.TranscriptionCommandHandler;
import com.salex.telegram.modules.transcription.commands.TranscriptionMessageFormatter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;

import java.util.Map;
import java.util.Objects;

/**
 * Handles automatic audio transcription and exposes the `/transcribe` command.
 */
@Service
public class TranscriptionHandlerService implements MessagingHandlerService {
    private static final Logger log = LoggerFactory.getLogger(TranscriptionHandlerService.class);

    private final TranscriptionService transcriptionService;
    private final TranscriptionMessageFormatter formatter;
    private final CommandHandler commandHandler;

    public TranscriptionHandlerService(TranscriptionService transcriptionService,
                                       TranscriptionMessageFormatter formatter) {
        this.transcriptionService = Objects.requireNonNull(transcriptionService, "transcriptionService");
        this.formatter = Objects.requireNonNull(formatter, "formatter");
        this.commandHandler = new TranscriptionCommandHandler(transcriptionService, formatter);
    }

    @Override
    public boolean canHandle(Update update, long userId) {
        if (update == null || !update.hasMessage()) {
            return false;
        }
        Message message = resolveTargetMessage(update.getMessage());
        return transcriptionService.supports(message);
    }

    @Override
    public void handle(Update update, SalexTelegramBot bot, long userId) {
        Message message = update.getMessage();
        if (message == null) {
            return;
        }

        Message target = resolveTargetMessage(message);
        long chatId = message.getChatId();
        Integer threadId = message.getMessageThreadId();

        try {
            bot.sendChatTyping(chatId, threadId);
            TranscriptionResult result = transcriptionService.transcribe(target);
            bot.sendMessage(chatId, threadId, formatter.formatResult(result));
        } catch (TranscriptionException ex) {
            bot.sendMessage(chatId, threadId, formatter.formatError(ex.getMessage()));
            log.error("Failed to transcribe audio for user {}: {}", userId, ex.getMessage(), ex);
        }
    }

    @Override
    public Map<String, CommandHandler> getCommands() {
        return Map.of(commandHandler.getName(), commandHandler);
    }

    private Message resolveTargetMessage(Message message) {
        return message != null && message.getReplyToMessage() != null
                ? message.getReplyToMessage()
                : message;
    }
}
