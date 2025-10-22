package com.salex.telegram.Transcription;

import com.salex.telegram.Transcoding.AudioResource;

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
