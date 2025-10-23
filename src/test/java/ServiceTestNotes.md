# Service Test Coverage Notes

Some services are not currently exercised by automated tests because of their tight coupling to external systems or rapidly evolving behaviour:

- `com.salex.telegram.application.services.ConversationalRelayService` – depends on live LLM integrations and persistence side effects that are still in flux.
- `com.salex.telegram.application.services.ticketing.TicketingHandlingService` – heavily tied to Telegram `Update` payloads and command routing that are being refactored.
- `com.salex.telegram.application.services.transcription.TranscriptionHandlerService` – similarly reliant on Telegram updates and concurrent typing indicators.
- `com.salex.telegram.user.infrastructure.JdbcUserService` – requires a real JDBC connection and database schema; better covered by integration tests once the data layer stabilises.

Add focused tests for these once their contracts and wiring are finalised.
