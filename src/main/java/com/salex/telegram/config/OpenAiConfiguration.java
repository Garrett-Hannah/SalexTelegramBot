package com.salex.telegram.config;

import com.salex.telegram.transcription.TelegramAudioDownloader;
import com.salex.telegram.transcription.TranscriptionClient;
import com.salex.telegram.transcription.TranscriptionService;
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
