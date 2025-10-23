package com.salex.telegram.conversation;

import java.util.List;

/**
 * Minimal abstraction for services capable of producing chat-style completions.
 */
public interface ChatCompletionClient {

    /**
     * Generates a reply for the supplied conversation history.
     *
     * @param conversation ordered list of messages ending with the latest user prompt
     * @return the assistant reply
     * @throws Exception when the underlying transport or service call fails
     */
    String complete(List<ConversationMessageRecord> conversation) throws Exception;
}
