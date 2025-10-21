package com.salex.telegram.transcoding;

import com.salex.telegram.transcription.TranscriptionException;
import org.bytedeco.ffmpeg.ffmpeg;
import org.bytedeco.javacpp.Loader;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Objects;

/**
 * Normalises Telegram audio payloads to a Whisper-friendly format using FFmpeg.
 */
//TODO maybe convert to a Service? idk is that right
public class AudioTranscoder {
    private static final String TARGET_EXTENSION = ".wav";
    private static final String TARGET_MIME = "audio/wav";

    private final AudioResource source;

    public AudioTranscoder(AudioResource source) {
        this.source = Objects.requireNonNull(source, "source");
    }

    /**
     * Converts the source audio to a mono 16 kHz WAV container ready for transcription.
     *
     * @return converted audio resource
     */
    public AudioResource toMono16kWav() {
        Path input = null;
        Path output = null;

        try {
            input = Files.createTempFile("salex-audio-in-", resolveInputExtension());
            Files.write(input, source.data());

            output = Files.createTempFile("salex-audio-out-", TARGET_EXTENSION);

            String ffmpegExecutable = Loader.load(ffmpeg.class);
            Process process = new ProcessBuilder(
                    ffmpegExecutable,
                    "-hide_banner",
                    "-loglevel", "error",
                    "-y",
                    "-i", input.toString(),
                    "-ar", "16000",
                    "-ac", "1",
                    output.toString()
            ).redirectErrorStream(true).start();

            String ffmpegOutput = drain(process.getInputStream());
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                throw new TranscriptionException("ffmpeg exited with code " + exitCode + (ffmpegOutput.isBlank() ? "" : ": " + ffmpegOutput));
            }

            byte[] wavData = Files.readAllBytes(output);
            return new AudioResource(rewriteFileName(source.fileName()), TARGET_MIME, wavData, source.durationSeconds());
        } catch (IOException ex) {
            throw new TranscriptionException("Failed to transcode audio: " + ex.getMessage(), ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new TranscriptionException("Transcoding interrupted: " + ex.getMessage(), ex);
        } finally {
            deleteQuietly(input);
            deleteQuietly(output);
        }
    }

    private String resolveInputExtension() {
        String name = source.fileName();
        if (name != null) {
            int idx = name.lastIndexOf('.');
            if (idx >= 0 && idx < name.length() - 1) {
                return name.substring(idx);
            }
        }
        String mime = source.mimeType();
        if (mime != null) {
            String lower = mime.toLowerCase(Locale.ROOT);
            if (lower.contains("ogg")) {
                return ".ogg";
            }
            if (lower.contains("mpeg")) {
                return ".mp3";
            }
            if (lower.contains("wav")) {
                return ".wav";
            }
            if (lower.contains("mp4")) {
                return ".mp4";
            }
        }
        return ".bin";
    }

    private String rewriteFileName(String original) {
        if (original == null || original.isBlank()) {
            return "audio-message" + TARGET_EXTENSION;
        }
        int idx = original.lastIndexOf('.');
        if (idx >= 0) {
            return original.substring(0, idx) + TARGET_EXTENSION;
        }
        return original + TARGET_EXTENSION;
    }

    private String drain(InputStream stream) throws IOException {
        return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
    }

    private void deleteQuietly(Path path) {
        if (path == null) {
            return;
        }
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
            // best effort cleanup
        }
    }
}
