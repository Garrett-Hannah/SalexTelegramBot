package com.salex.telegram.Transcription;

/**
 * Output returned by a transcription backend.
 */
public record TranscriptionResult(String text, String model, double durationSeconds) {
    public TranscriptionResult {
        if (text == null) {
            text = "";
        }
        if (model == null) {
            model = "unknown";
        }
        if (Double.isNaN(durationSeconds) || durationSeconds < 0) {
            durationSeconds = 0d;
        }
    }
}
