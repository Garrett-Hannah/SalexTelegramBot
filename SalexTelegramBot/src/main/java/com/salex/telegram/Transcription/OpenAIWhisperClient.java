package com.salex.telegram.Transcription;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

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
public class OpenAIWhisperClient implements TranscriptionClient {
    private final HttpClient httpClient;
    private final String apiKey;
    private final String model;
    private final URI endpoint;

    public OpenAIWhisperClient(HttpClient httpClient, String apiKey, String model) {
        this(httpClient, apiKey, model, URI.create("https://api.openai.com/v1/audio/transcriptions"));
    }

    OpenAIWhisperClient(HttpClient httpClient, String apiKey, String model, URI endpoint) {
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient");
        this.apiKey = Objects.requireNonNull(apiKey, "apiKey");
        this.model = model == null || model.isBlank() ? "gpt-4o-transcribe" : model;
        this.endpoint = Objects.requireNonNull(endpoint, "endpoint");
    }

    @Override
    public TranscriptionResult transcribe(AudioResource audio) {
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
