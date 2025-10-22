package com.salex.telegram.application.config;

import com.salex.telegram.transcription.application.TranscriptionService;
import com.salex.telegram.transcription.domain.TranscriptionClient;
import com.salex.telegram.transcription.infrastructure.TelegramAudioDownloader;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenAiConfiguration {

    @Bean
    TranscriptionService transcriptionService(TelegramAudioDownloader downloader,
                                              TranscriptionClient client) {
        return new TranscriptionService(downloader, client);
    }
}
