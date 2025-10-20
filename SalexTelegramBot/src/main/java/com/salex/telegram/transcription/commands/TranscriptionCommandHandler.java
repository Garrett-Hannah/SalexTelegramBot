package com.salex.telegram.transcription.commands;

import com.salex.telegram.bot.SalexTelegramBot;
import com.salex.telegram.transcription.TranscriptionException;
import com.salex.telegram.transcription.TranscriptionResult;
import com.salex.telegram.transcription.TranscriptionService;
import com.salex.telegram.modules.CommandHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;

import java.util.Objects;

/**
 * Telegram command handler that transcribes audio messages.
 */
public class TranscriptionCommandHandler implements CommandHandler {
    private static final Logger log = LoggerFactory.getLogger(TranscriptionCommandHandler.class);
    private final TranscriptionService transcriptionService;
    private final TranscriptionMessageFormatter formatter;

    public TranscriptionCommandHandler(TranscriptionService transcriptionService,
                                       TranscriptionMessageFormatter formatter) {
        this.transcriptionService = Objects.requireNonNull(transcriptionService, "transcriptionService");
        this.formatter = Objects.requireNonNull(formatter, "formatter");
    }

    @Override
    public String getName() {
        return "/transcribe";
    }

    @Override
    public String getDescription() {
        return "Convert a voice message to text.";
    }

    @Override
    public void handle(Update update, SalexTelegramBot bot, long userId) {
        if (update == null || !update.hasMessage()) {
            return;
        }

        Message message = update.getMessage();
        long chatId = message.getChatId();
        Integer threadId = message.getMessageThreadId();
        Message target = message.getReplyToMessage() != null ? message.getReplyToMessage() : message;

        if (!transcriptionService.supports(target)) {
            bot.sendMessage(chatId, threadId, formatter.formatUsage());
            log.debug("User {} invoked transcription without audio payload", userId);
            return;
        }

        try {
            TranscriptionResult result = transcriptionService.transcribe(target);
            bot.sendMessage(chatId, threadId, formatter.formatResult(result));
            log.info("Delivered transcription to user {}", userId);
        } catch (TranscriptionException ex) {
            bot.sendMessage(chatId, threadId, formatter.formatError(ex.getMessage()));
            log.error("Transcription failed for user {}: {}", userId, ex.getMessage(), ex);
        }
    }
}
