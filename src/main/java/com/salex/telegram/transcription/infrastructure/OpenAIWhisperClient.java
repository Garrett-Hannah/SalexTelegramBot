package com.salex.telegram.Transcription.infrastructure;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.salex.telegram.transcription.domain.TranscriptionClient;
import com.salex.telegram.transcription.domain.TranscriptionException;
import com.salex.telegram.transcription.domain.TranscriptionResult;
import com.salex.telegram.transcription.infrastructure.transcoding.AudioResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Objects;
import java.util.UUID;

/**
 * Transcription client that targets OpenAI's Whisper API.
 */
@Service
public class OpenAIWhisperClient implements TranscriptionClient {

    private static final Logger log = LoggerFactory.getLogger(OpenAIWhisperClient.class);

    private final HttpClient httpClient;
    private final String apiKey;
    private final String model;
    private final URI endpoint;

    public OpenAIWhisperClient(HttpClient httpClient,
                               @Value("${bot.openai.api-key:${OPENAI_API_KEY:}}") String apiKey,
                               @Value("${bot.openai.whisper-model:gpt-4o-transcribe}") String model,
                               @Value("${bot.openai.endpoint:https://api.openai.com/v1/audio/transcriptions}") String endpointUrl) {
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient");
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.model = (model == null || model.isBlank()) ? "gpt-4o-transcribe" : model.trim();
        String url = (endpointUrl == null || endpointUrl.isBlank())
                ? "https://api.openai.com/v1/audio/transcriptions"
                : endpointUrl.trim();
        this.endpoint = URI.create(url);
        if (this.apiKey.isEmpty()) {
            log.warn("OpenAI Whisper API key not configured; transcription requests will fail");
        }
    }

    @Override
    public TranscriptionResult transcribe(AudioResource audio) {
        if (apiKey.isEmpty()) {
            throw new TranscriptionException("OPENAI_API_KEY not configured");
        }
        try {
            HttpRequest request = buildRequest(audio);
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 400) {
                throw new TranscriptionException("Transcription failed with status " + response.statusCode() + ": " + response.body());
            }
            return parseResponse(response.body(), audio.durationSeconds());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new TranscriptionException("Transcription request interrupted: " + ex.getMessage(), ex);
        } catch (IOException ex) {
            throw new TranscriptionException("Transcription request failed: " + ex.getMessage(), ex);
        }
    }

    private HttpRequest buildRequest(AudioResource audio) throws IOException {
        String boundary = "----Boundary" + UUID.randomUUID();
        byte[] body = buildMultipartBody(boundary, audio);

        return HttpRequest.newBuilder()
                .uri(endpoint)
                .timeout(Duration.ofSeconds(60))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                .build();
    }

    private byte[] buildMultipartBody(String boundary, AudioResource audio) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (OutputStreamWriter writer = new OutputStreamWriter(buffer, StandardCharsets.UTF_8)) {
            writer.write("--" + boundary + "\r\n");
            writer.write("Content-Disposition: form-data; name=\"model\"\r\n\r\n");
            writer.write(model);
            writer.write("\r\n");

            writer.write("--" + boundary + "\r\n");
            writer.write("Content-Disposition: form-data; name=\"response_format\"\r\n\r\n");
            writer.write("json");
            writer.write("\r\n");

            writer.write("--" + boundary + "\r\n");
            writer.write("Content-Disposition: form-data; name=\"temperature\"\r\n\r\n");
            writer.write("0");
            writer.write("\r\n");

            writer.write("--" + boundary + "\r\n");
            writer.write("Content-Disposition: form-data; name=\"file\"; filename=\"" + audio.fileName() + "\"\r\n");
            writer.write("Content-Type: " + audio.mimeType() + "\r\n\r\n");
            writer.flush();
            buffer.write(audio.data());
            writer.write("\r\n");
            writer.write("--" + boundary + "--\r\n");
        }
        return buffer.toByteArray();
    }

    private TranscriptionResult parseResponse(String body, double originalDuration) {
        JsonObject json = JsonParser.parseString(body).getAsJsonObject();
        JsonElement textElement = json.get("text");
        String text = textElement == null ? "" : textElement.getAsString();
        String usedModel = json.has("model") ? json.get("model").getAsString() : model;
        double duration = json.has("duration") ? json.get("duration").getAsDouble() : originalDuration;
        return new TranscriptionResult(text, usedModel, duration);
    }
}
