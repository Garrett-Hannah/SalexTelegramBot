package com.salex.telegram.Transcription;

import com.salex.telegram.Transcoding.AudioTranscoder;
import org.telegram.telegrambots.meta.api.objects.Message;

import java.util.Objects;

/**
 * Orchestrates Telegram audio downloads and transcription requests.
 */
public class TranscriptionService {
    private final TelegramAudioDownloader audioDownloader;
    private final TranscriptionClient transcriptionClient;

    public TranscriptionService(TelegramAudioDownloader audioDownloader,
                                TranscriptionClient transcriptionClient) {
        this.audioDownloader = Objects.requireNonNull(audioDownloader, "audioDownloader");
        this.transcriptionClient = Objects.requireNonNull(transcriptionClient, "transcriptionClient");
    }

    /**
     * Determines whether the supplied message contains audio content that can be transcribed.
     *
     * @param message Telegram message to inspect
     * @return {@code true} when the message carries voice, audio, or video note content
     */
    public boolean supports(Message message) {
        if (message == null) {
            return false;
        }
        return message.hasVoice() || message.hasAudio() || message.hasVideoNote();
    }

    /**
     * Downloads the audio contained in the Telegram message and forwards it to the transcription backend.
     *
     * @param message Telegram message that carries the audio payload
     * @return transcription result returned by the downstream provider
     */
    public TranscriptionResult transcribe(Message message) {
        if (!supports(message)) {
            throw new TranscriptionException("Message does not include transcribable audio.");
        }

        AudioResource downloaded = audioDownloader.download(message);
        AudioResource prepared = new AudioTranscoder(downloaded).toMono16kWav();
        return transcriptionClient.transcribe(prepared);
    }
}
