package com.salex.telegram.Ticketing;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class TicketSessionManagerContractTest {

    @Test
    void sessionManagerDefinesLifecycleOperations() {
        assertThat(TicketSessionManager.class).isInterface();

        Map<String, Class<?>[]> signatures = collectSignatures(TicketSessionManager.class);

        assertThat(signatures).containsKeys(
                "openSession",
                "getDraft",
                "updateDraft",
                "closeSession"
        );
    }

    private Map<String, Class<?>[]> collectSignatures(Class<?> type) {
        return java.util.Arrays.stream(type.getDeclaredMethods())
                .collect(Collectors.toMap(Method::getName, Method::getParameterTypes));
    }
}
