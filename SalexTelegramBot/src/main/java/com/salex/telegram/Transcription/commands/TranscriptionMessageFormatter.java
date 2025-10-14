package com.salex.telegram.Transcription.commands;

import com.salex.telegram.Transcription.TranscriptionResult;

/**
 * Formats user-facing messages for transcription flows.
 */
public class TranscriptionMessageFormatter {

    public String formatUsage() {
        return """
                Send a voice message or reply with /transcribe to convert audio into text.
                """.trim();
    }

    public String formatResult(TranscriptionResult result) {
        return """
                ✏️ Transcription (%s)

                %s
                """.formatted(result.model(), result.text().isEmpty() ? "[No speech detected]" : result.text()).trim();
    }

    public String formatError(String error) {
        return "[Transcription Error] " + error;
    }
}
