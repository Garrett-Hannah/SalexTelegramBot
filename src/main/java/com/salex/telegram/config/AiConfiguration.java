package com.salex.telegram.config;

import com.salex.telegram.ai.ChatCompletionClient;
import com.salex.telegram.ai.OpenAIChatCompletionClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import java.net.http.HttpClient;

/**
 * Registers AI-related collaborators such as the OpenAI chat completion client.
 */
@Configuration
public class AiConfiguration {
    private static final Logger log = LoggerFactory.getLogger(AiConfiguration.class);

    @Bean
    HttpClient httpClient() {
        return HttpClient.newHttpClient();
    }

    @Bean
    ChatCompletionClient chatCompletionClient(Environment environment, HttpClient httpClient) {
        String apiKey = firstNonBlank(environment.getProperty("OPENAI_API_KEY"),
                System.getenv("OPENAI_API_KEY"));
        if (apiKey == null) {
            log.warn("OPENAI_API_KEY not provided; conversational relay will be unavailable");
            return conversation -> {
                throw new IllegalStateException("OPENAI_API_KEY not configured");
            };
        }
        String model = firstNonBlank(environment.getProperty("OPENAI_CHAT_MODEL"),
                System.getenv("OPENAI_CHAT_MODEL"),
                "gpt-4o-mini");
        return new OpenAIChatCompletionClient(httpClient, apiKey, model);
    }

    private String firstNonBlank(String... candidates) {
        if (candidates == null) {
            return null;
        }
        for (String candidate : candidates) {
            if (candidate != null && !candidate.isBlank()) {
                return candidate;
            }
        }
        return null;
    }
}
