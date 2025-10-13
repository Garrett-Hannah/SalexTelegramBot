package com.salex.telegram.ticketing;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class TicketRepositoryContractTest {

    @Test
    void repositoryIsInterfaceWithExpectedMethods() {
        assertThat(TicketRepository.class).isInterface();

        Map<String, Class<?>[]> signatures = collectSignatures(TicketRepository.class);

        assertThat(signatures).containsKeys(
                "createDraftTicket",
                "findById",
                "findAllForUser",
                "save"
        );

        assertThat(signatures.get("findById"))
                .containsExactly(long.class);
    }

    private Map<String, Class<?>[]> collectSignatures(Class<?> type) {
        return java.util.Arrays.stream(type.getDeclaredMethods())
                .collect(Collectors.toMap(Method::getName, Method::getParameterTypes));
    }
}
