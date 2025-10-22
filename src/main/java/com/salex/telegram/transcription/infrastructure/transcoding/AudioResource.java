package com.salex.telegram.transcription.infrastructure.transcoding;

import java.util.Arrays;

/**
 * Represents an audio payload retrieved from Telegram for transcription.
 */
public record AudioResource(String fileName, String mimeType, byte[] data, int durationSeconds) {

    public AudioResource {
        if (fileName == null || fileName.isBlank()) {
            fileName = "audio-message";
        }
        if (mimeType == null || mimeType.isBlank()) {
            mimeType = "application/octet-stream";
        }
        data = data == null ? new byte[0] : Arrays.copyOf(data, data.length);
    }

    /**
     * @return the size of the audio payload in bytes
     */
    public long size() {
        return data.length;
    }
}
