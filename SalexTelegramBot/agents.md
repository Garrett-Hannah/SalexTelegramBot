# Agents Notes

## Ticket Storage Backends
- `com.salex.telegram.Ticketing.InMemory.*` remains the default for quick local runs without PostgreSQL.
- `com.salex.telegram.Ticketing.OnServer.ServerTicketRepository` and `ServerTicketSessionManager` require an active JDBC `Connection` and the tables listed in the README (`users`, `messages`, `ticket_sessions`, plus `tickets`).
- When wiring new services, choose the repository/session manager pair based on whether a connection is present.

## Database Checklist
1. Ensure the `tickets` table exposes columns: `id`, `status`, `priority`, `created_at`, `updated_at`, `created_by`, `assignee`, `summary`, `details`.
2. Apply the schemas for `users`, `messages`, and `ticket_sessions` (see README).
3. Provide credentials through the environment variables described in the README.

## Database Schema Snapshot
- `users`: `id`, `telegram_id`, `username`, `first_name`, `last_name`, `created_at` (timestamp without time zone).
- `messages`: `id`, `user_id`, `chat_id`, `text`, `reply`, `created_at` (timestamp without time zone).
- `ticket_sessions`: `chat_id`, `user_id`, `ticket_id`, `summary`, `priority`, `details`.
- `tickets`: `id`, `status`, `priority`, `created_at`, `updated_at` (timestamps with time zone), `created_by`, `assignee`, `summary`, `details`.

## Useful JVM Entrypoint
- Main class: `Main` in `src/main/java/Main.java`.
- Classpath launch pattern: `java -cp target/telegram-bot-demo-1.0-SNAPSHOT.jar:target/dependency/* Main`.

## Debugging Tips
- Toggle verbose logging by checking a `DEBUG` environment variable inside `TelegramBot` when adding new diagnostics.
- SQL failures from repositories/session managers are surfaced as runtime exceptions; catch earlier in the call stack if you need user-friendly messaging.

## Command Reference
- `/menu` – Enumerates registered commands.
- `/ticket new` – Creates a draft ticket and opens a session.
- `/ticket list|<id>|close` – Operates on persisted tickets via the repository.
