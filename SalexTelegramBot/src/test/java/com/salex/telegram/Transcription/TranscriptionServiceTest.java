package com.salex.telegram.Transcription;

import com.salex.telegram.Transcoding.AudioResource;
import org.junit.jupiter.api.Test;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Voice;
import org.telegram.telegrambots.meta.api.objects.Update;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TranscriptionServiceTest {

    @Test
    void supportsReturnsTrueForVoiceMessage() {
        TranscriptionService service = new TranscriptionService(new StubDownloader(), audio -> null);
        Message message = new Message();
        message.setVoice(new Voice());

        assertThat(service.supports(message)).isTrue();
    }

    @Test
    void supportsReturnsFalseWhenNoAudio() {
        TranscriptionService service = new TranscriptionService(new StubDownloader(), audio -> null);
        Message message = new Message();

        assertThat(service.supports(message)).isFalse();
    }

    @Test
    void transcribeDelegatesToClient() {
        TranscriptionResult expected = new TranscriptionResult("hello world", "mock", 1.5);
        TranscriptionService service = new TranscriptionService(new StubDownloader(), audio -> expected);

        Message message = new Message();
        Voice voice = new Voice();
        voice.setFileId("file");
        message.setVoice(voice);

        TranscriptionResult result = service.transcribe(message);

        assertThat(result).isEqualTo(expected);
    }

    @Test
    void transcribeThrowsWhenUnsupportedMessage() {
        TranscriptionService service = new TranscriptionService(new StubDownloader(), audio -> null);
        Message message = new Message();

        assertThatThrownBy(() -> service.transcribe(message))
                .isInstanceOf(TranscriptionException.class)
                .hasMessageContaining("transcribable audio");
    }

    private static class StubDownloader extends TelegramAudioDownloader {
        StubDownloader() {
            super(new TelegramLongPollingBot("token") {
                @Override
                public void onUpdateReceived(Update update) {
                    // no-op
                }

                @Override
                public String getBotUsername() {
                    return "stub";
                }
            });
        }

        @Override
        public AudioResource download(Message message) {
            return new AudioResource("file.ogg", "audio/ogg", new byte[]{1, 2, 3}, 1);
        }
    }
}
