package com.salex.telegram.transcription.application;

import com.salex.telegram.transcription.domain.TranscriptionClient;
import com.salex.telegram.transcription.domain.TranscriptionException;
import com.salex.telegram.transcription.domain.TranscriptionResult;
import com.salex.telegram.transcription.infrastructure.TelegramAudioDownloader;
import com.salex.telegram.transcription.infrastructure.transcoding.AudioResource;
import com.salex.telegram.transcription.infrastructure.transcoding.AudioTranscoder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedConstruction;
import org.mockito.junit.jupiter.MockitoExtension;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.objects.Audio;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.VideoNote;
import org.telegram.telegrambots.meta.api.objects.Voice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TranscriptionServiceTest {

    @Mock
    private TranscriptionClient transcriptionClient;

    private StubTelegramAudioDownloader audioDownloader;
    private TranscriptionService service;

    @BeforeEach
    void setUp() {
        audioDownloader = new StubTelegramAudioDownloader();
        service = new TranscriptionService(audioDownloader, transcriptionClient);
    }

    @Test
    void supportsReturnsTrueForVoiceAudioOrVideoNote() {
        Message voiceMessage = new Message();
        Voice voice = new Voice();
        voice.setFileId("voice");
        voiceMessage.setVoice(voice);
        assertThat(service.supports(voiceMessage)).isTrue();

        Message audioMessage = new Message();
        Audio audio = new Audio();
        audio.setFileId("audio");
        audioMessage.setAudio(audio);
        assertThat(service.supports(audioMessage)).isTrue();

        Message videoMessage = new Message();
        VideoNote note = new VideoNote();
        note.setFileId("video");
        videoMessage.setVideoNote(note);
        assertThat(service.supports(videoMessage)).isTrue();
    }

    @Test
    void transcribeDelegatesToDownloaderTranscoderAndClient() {
        AudioResource downloaded = new AudioResource("clip.ogg", "audio/ogg", new byte[]{1, 2, 3}, 5);
        AudioResource transcoded = new AudioResource("clip.wav", "audio/wav", new byte[]{4, 5}, 5);
        TranscriptionResult expected = new TranscriptionResult("text", "whisper", 5.0);

        audioDownloader.setNext(downloaded);
        when(transcriptionClient.transcribe(transcoded)).thenReturn(expected);

        Message message = new Message();
        Audio audio = new Audio();
        audio.setFileId("audio");
        message.setAudio(audio);

        try (MockedConstruction<AudioTranscoder> construction = mockConstruction(
                AudioTranscoder.class,
                (mock, context) -> when(mock.toMono16kWav()).thenReturn(transcoded))) {

            TranscriptionResult result = service.transcribe(message);
            assertThat(result).isSameAs(expected);
            assertThat(construction.constructed()).hasSize(1);
        }

        verify(transcriptionClient).transcribe(transcoded);
    }

    @Test
    void transcribeThrowsWhenMessageUnsupported() {
        Message message = new Message();

        assertThrows(TranscriptionException.class, () -> service.transcribe(message));
        verifyNoInteractions(transcriptionClient);
    }

    private static final class StubTelegramAudioDownloader extends TelegramAudioDownloader {
        private static final TelegramLongPollingBot BOT = new TelegramLongPollingBot("token") {
            @Override
            public void onUpdateReceived(Update update) {
                // no-op
            }

            @Override
            public String getBotUsername() {
                return "stub";
            }
        };

        private AudioResource next;

        StubTelegramAudioDownloader() {
            super(BOT);
        }

        void setNext(AudioResource resource) {
            this.next = resource;
        }

        @Override
        public AudioResource download(Message message) {
            if (next == null) {
                throw new IllegalStateException("No stub audio configured");
            }
            return next;
        }
    }
}
