package com.salex.telegram.conversation;

import com.salex.telegram.infrastructure.messaging.LoggedMessage;
import com.salex.telegram.infrastructure.messaging.MessageRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ConversationContextServiceTest {

    private static final long CHAT_ID = 123L;
    private static final long USER_ID = 456L;

    @Mock
    private MessageRepository messageRepository;

    private ConversationContextService service;

    @BeforeEach
    void setUp() {
        service = new ConversationContextService(messageRepository, 6);
    }

    @Test
    void buildRequestMessagesSeedsFromRepositoryOnce() {
        when(messageRepository.findRecent(CHAT_ID, USER_ID, 3)).thenReturn(List.of(
                new LoggedMessage(USER_ID, CHAT_ID, "hello", "hi there"),
                new LoggedMessage(USER_ID, CHAT_ID, "status?", "working on it")
        ));

        List<ConversationMessageRecord> first = service.buildRequestMessages(CHAT_ID, USER_ID, "new info");

        assertThat(first).extracting(ConversationMessageRecord::content)
                .containsExactly("hello", "hi there", "status?", "working on it", "new info");
        verify(messageRepository).findRecent(CHAT_ID, USER_ID, 3);

        List<ConversationMessageRecord> second = service.buildRequestMessages(CHAT_ID, USER_ID, "follow up");

        assertThat(second).last().extracting(ConversationMessageRecord::content).isEqualTo("follow up");
        verifyNoMoreInteractions(messageRepository);
    }

    @Test
    void recordExchangeAddsMessagesToCache() {
        when(messageRepository.findRecent(CHAT_ID, USER_ID, 3)).thenReturn(List.of());

        service.recordExchange(CHAT_ID, USER_ID, "issue details", "ack");

        List<ConversationMessageRecord> request = service.buildRequestMessages(CHAT_ID, USER_ID, "next step");
        assertThat(request).extracting(ConversationMessageRecord::content)
                .containsExactly("issue details", "ack", "next step");
    }

    @Test
    void resetConversationForcesRepositoryReload() {
        when(messageRepository.findRecent(CHAT_ID, USER_ID, 3)).thenReturn(List.of(
                new LoggedMessage(USER_ID, CHAT_ID, "old", "reply")
        ));

        service.buildRequestMessages(CHAT_ID, USER_ID, "first");
        service.resetConversation(CHAT_ID, USER_ID);

        when(messageRepository.findRecent(CHAT_ID, USER_ID, 3)).thenReturn(List.of(
                new LoggedMessage(USER_ID, CHAT_ID, "refreshed", "response")
        ));

        List<ConversationMessageRecord> refreshed = service.buildRequestMessages(CHAT_ID, USER_ID, "again");
        assertThat(refreshed).extracting(ConversationMessageRecord::content)
                .containsExactly("refreshed", "response", "again");
        verify(messageRepository, times(2)).findRecent(CHAT_ID, USER_ID, 3);
    }
}
