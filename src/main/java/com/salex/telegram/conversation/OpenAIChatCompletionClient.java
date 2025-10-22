package com.salex.telegram.conversation;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;

/**
 * Small HTTP client that calls OpenAI's chat completions endpoint.
 */
public class OpenAIChatCompletionClient implements ChatCompletionClient {
    private static final Logger log = LoggerFactory.getLogger(OpenAIChatCompletionClient.class);
    private final HttpClient httpClient;
    private final String apiKey;
    private final String model;
    private final URI endpoint;

    public OpenAIChatCompletionClient(HttpClient httpClient, String apiKey, String model) {
        this(httpClient, apiKey, model, URI.create("https://api.openai.com/v1/chat/completions"));
    }

    public OpenAIChatCompletionClient(HttpClient httpClient, String apiKey, String model, URI endpoint) {
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient");
        this.apiKey = Objects.requireNonNull(apiKey, "apiKey");
        if (apiKey.isBlank()) {
            throw new IllegalArgumentException("apiKey must not be blank");
        }
        this.model = Objects.requireNonNull(model, "model");
        if (model.isBlank()) {
            throw new IllegalArgumentException("model must not be blank");
        }
        this.endpoint = Objects.requireNonNull(endpoint, "endpoint");
    }

    @Override
    public String complete(List<ConversationMessage> conversation) throws Exception {
        Objects.requireNonNull(conversation, "conversation");

        JsonObject payload = new JsonObject();
        payload.addProperty("model", model);
        payload.add("messages", toPayloadMessages(conversation));

        HttpRequest request = HttpRequest.newBuilder()
                .uri(endpoint)
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(payload.toString(), StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        log.debug("Chat completion responded with status {}", response.statusCode());
        if (response.statusCode() / 100 != 2) {
            throw new IllegalStateException("Chat completion request failed with status " + response.statusCode());
        }

        JsonObject root = JsonParser.parseString(response.body()).getAsJsonObject();
        JsonArray choices = root.getAsJsonArray("choices");
        if (choices == null || choices.size() == 0) {
            throw new IllegalStateException("Chat completion response missing choices");
        }
        JsonObject message = choices.get(0).getAsJsonObject().getAsJsonObject("message");
        if (message == null || !message.has("content")) {
            throw new IllegalStateException("Chat completion response missing message content");
        }
        return message.get("content").getAsString();
    }

    private JsonArray toPayloadMessages(List<ConversationMessage> conversation) {
        JsonArray messages = new JsonArray();
        if (conversation.isEmpty()) {
            JsonObject fallbackMessage = new JsonObject();
            fallbackMessage.addProperty("role", "user");
            fallbackMessage.addProperty("content", "");
            messages.add(fallbackMessage);
            return messages;
        }

        for (ConversationMessage entry : conversation) {
            JsonObject messageObject = new JsonObject();
            messageObject.addProperty("role", entry.role());
            messageObject.addProperty("content", entry.content());
            messages.add(messageObject);
        }
        return messages;
    }
}
