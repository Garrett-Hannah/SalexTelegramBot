package com.salex.telegram.transcription.domain;

import com.salex.telegram.transcription.infrastructure.transcoding.AudioResource;

/**
 * Contract for services capable of converting audio payloads into text.
 */
public interface TranscriptionClient {
    /**
     * Processes the supplied audio payload and returns the transcription result.
     *
     * @param audio audio payload to transcribe
     * @return transcription output
     */
    TranscriptionResult transcribe(AudioResource audio);
}
