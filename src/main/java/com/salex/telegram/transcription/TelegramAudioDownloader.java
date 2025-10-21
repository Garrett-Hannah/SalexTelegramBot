package com.salex.telegram.transcription;

import com.salex.telegram.transcoding.AudioResource;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.GetFile;
import org.telegram.telegrambots.meta.api.objects.Audio;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.VideoNote;
import org.telegram.telegrambots.meta.api.objects.Voice;
import org.telegram.telegrambots.meta.api.objects.File;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;

/**
 * Downloads audio payloads from Telegram using the bot API.
 */
public class TelegramAudioDownloader {
    private final TelegramLongPollingBot bot;

    public TelegramAudioDownloader(TelegramLongPollingBot bot) {
        this.bot = Objects.requireNonNull(bot, "bot");
    }

    /**
     * Fetches the audio content associated with the supplied message.
     *
     * @param message Telegram message containing voice, audio, or video note content
     * @return normalised audio resource ready for transcription
     */
    public AudioResource download(Message message) {
        try {
            return doDownload(message);
        } catch (TelegramApiException | IOException ex) {
            throw new TranscriptionException("Failed to download audio: " + ex.getMessage(), ex);
        }
    }

    private AudioResource doDownload(Message message) throws TelegramApiException, IOException {
        String fileId;
        String fileName;
        String mimeType;
        int durationSeconds = 0;

        if (message.hasVoice()) {
            Voice voice = message.getVoice();
            fileId = voice.getFileId();
            fileName = voice.getFileUniqueId() + ".oga";
            mimeType = voice.getMimeType();
            durationSeconds = voice.getDuration();
        } else if (message.hasAudio()) {
            Audio audio = message.getAudio();
            fileId = audio.getFileId();
            fileName = resolveAudioFileName(audio);
            mimeType = audio.getMimeType();
            durationSeconds = audio.getDuration();
        } else if (message.hasVideoNote()) {
            VideoNote videoNote = message.getVideoNote();
            fileId = videoNote.getFileId();
            fileName = videoNote.getFileUniqueId() + ".mp4";
            mimeType = "video/mp4";
            durationSeconds = videoNote.getDuration();
        } else {
            throw new TranscriptionException("Message does not contain downloadable audio.");
        }

        GetFile getFile = new GetFile();
        getFile.setFileId(fileId);
        File file = bot.execute(getFile);

        try (InputStream inputStream = bot.downloadFileAsStream(file)) {
            byte[] data = inputStream.readAllBytes();
            return new AudioResource(fileName, mimeType, data, durationSeconds);
        }
    }

    private String resolveAudioFileName(Audio audio) {
        if (audio.getFileName() != null && !audio.getFileName().isBlank()) {
            return audio.getFileName();
        }
        return audio.getFileUniqueId() + guessExtension(audio.getMimeType());
    }

    private String guessExtension(String mimeType) {
        if (mimeType == null) {
            return ".bin";
        }
        if (mimeType.contains("mpeg")) {
            return ".mp3";
        }
        if (mimeType.contains("ogg")) {
            return ".ogg";
        }
        if (mimeType.contains("wav")) {
            return ".wav";
        }
        return ".bin";
    }
}
