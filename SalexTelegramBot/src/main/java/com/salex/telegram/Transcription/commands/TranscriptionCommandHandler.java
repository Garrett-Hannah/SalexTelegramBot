package com.salex.telegram.Transcription.commands;

import com.salex.telegram.Bot.SalexTelegramBot;
import com.salex.telegram.Transcription.TranscriptionException;
import com.salex.telegram.Transcription.TranscriptionResult;
import com.salex.telegram.Transcription.TranscriptionService;
import com.salex.telegram.commanding.CommandHandler;
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
        Message target = message.getReplyToMessage() != null ? message.getReplyToMessage() : message;

        if (!transcriptionService.supports(target)) {
            bot.sendMessage(chatId, formatter.formatUsage());
            log.debug("User {} invoked transcription without audio payload", userId);
            return;
        }

        try {
            TranscriptionResult result = transcriptionService.transcribe(target);
            bot.sendMessage(chatId, formatter.formatResult(result));
            log.info("Delivered transcription to user {}", userId);
        } catch (TranscriptionException ex) {
            bot.sendMessage(chatId, formatter.formatError(ex.getMessage()));
            log.error("Transcription failed for user {}: {}", userId, ex.getMessage(), ex);
        }
    }
}
