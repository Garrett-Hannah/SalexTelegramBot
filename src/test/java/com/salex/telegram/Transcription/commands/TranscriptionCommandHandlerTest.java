package com.salex.telegram.Transcription.commands;

import com.salex.telegram.Bot.SalexTelegramBot;
import com.salex.telegram.Transcoding.AudioResource;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.User;
import org.telegram.telegrambots.meta.api.objects.Voice;

import java.util.ArrayList;
import java.util.List;

class TranscriptionCommandHandlerTest {

    @Test
    void handleRepliesWithTranscriptionWhenAudioProvided() {
        StubDownloader downloader = new StubDownloader();
        TranscriptionResult expected = new TranscriptionResult("Hello world", "mock", 1);
        TranscriptionService service = new TranscriptionService(downloader, audio -> expected);
        TranscriptionMessageFormatter formatter = new TranscriptionMessageFormatter();
        RecordingBotSalex bot = new RecordingBotSalex(service, formatter);
        TranscriptionCommandHandler handler = new TranscriptionCommandHandler(service, formatter);

        Update update = buildUpdateWithCommand("/transcribe", buildVoiceMessage());

        handler.handle(update, bot, 7L);

        Assertions.assertThat(bot.sentMessages)
                .containsExactly(formatter.formatResult(expected));
    }

    @Test
    void handleInstructsUserWhenNoAudioPresent() {
        StubDownloader downloader = new StubDownloader();
        TranscriptionService service = new TranscriptionService(downloader, audio -> null);
        TranscriptionMessageFormatter formatter = new TranscriptionMessageFormatter();
        RecordingBotSalex bot = new RecordingBotSalex(service, formatter);
        TranscriptionCommandHandler handler = new TranscriptionCommandHandler(service, formatter);

        Update update = buildUpdateWithCommand("/transcribe", new Message());

        handler.handle(update, bot, 7L);

        Assertions.assertThat(bot.sentMessages)
                .containsExactly(formatter.formatUsage());
    }

    private Update buildUpdateWithCommand(String commandText, Message reply) {
        Update update = new Update();
        Message command = new Message();
        command.setChatId(123L);
        command.setText(commandText);
        User user = new User();
        user.setId(999L);
        command.setFrom(user);
        if (reply != null) {
            command.setReplyToMessage(reply);
        }
        update.setMessage(command);
        return update;
    }

    private Message buildVoiceMessage() {
        Message message = new Message();
        message.setChatId(123L);
        Voice voice = new Voice();
        voice.setFileId("file");
        message.setVoice(voice);
        return message;
    }

    private static class RecordingBotSalex extends SalexTelegramBot {
        private final List<String> sentMessages = new ArrayList<>();

        RecordingBotSalex(TranscriptionService service, TranscriptionMessageFormatter formatter) {
            super("token", "bot", null, null, null, service, formatter, null);
        }

        @Override
        public void onUpdateReceived(Update update) {
            // not required for tests
        }

        @Override
        public void sendMessage(long chatId, String text) {
            sentMessages.add(text);
        }
    }

    private static class StubDownloader extends com.salex.telegram.Transcription.TelegramAudioDownloader {
        StubDownloader() {
            super(new TestBot());
        }

        @Override
        public AudioResource download(Message message) {
            return new AudioResource("file.ogg", "audio/ogg", new byte[]{1}, 1);
        }
    }

    private static class TestBot extends org.telegram.telegrambots.bots.TelegramLongPollingBot {
        TestBot() {
            super("token");
        }

        @Override
        public void onUpdateReceived(Update update) {
            // not required
        }

        @Override
        public String getBotUsername() {
            return "stub";
        }
    }
}
