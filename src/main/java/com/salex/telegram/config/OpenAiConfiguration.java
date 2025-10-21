package com.salex.telegram.config;

import com.salex.telegram.transcription.TelegramAudioDownloader;
import com.salex.telegram.transcription.TranscriptionClient;
import com.salex.telegram.transcription.TranscriptionService;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "bot.openai")
public class OpenAiConfiguration {
    private String apiKey;
    private String whisperModel;
    private String endpoint;

    TelegramAudioDownloader downloader;
    TranscriptionClient client;

    @Bean
    TranscriptionService transcriptionService(){
        return new TranscriptionService(downloader, client);
    }
}
