## Salex Telegram Bot

Salex Telegram Bot is a Java 23 Telegram bot that combines a conversational assistant backed by OpenAI with an interactive ticketing workflow. It ships with in-memory ticket storage for quick experiments while also persisting user interactions to PostgreSQL for auditing and analytics.

---

### Table of Contents
- [Features](#features)
- [Architecture](#architecture)
- [Prerequisites](#prerequisites)
- [Environment Variables](#environment-variables)
- [Database Setup](#database-setup)
- [Build](#build)
- [Run](#run)
- [Bot Commands](#bot-commands)
- [Ticket Workflow](#ticket-workflow)
- [Debugging & Troubleshooting](#debugging--troubleshooting)
- [Testing](#testing)
- [Extending the Bot](#extending-the-bot)
- [Project Structure](#project-structure)

---

### Features
- Long-polling Telegram bot built on `org.telegram:telegrambots` 6.9.7.1.
- Interactive ticket creation flow with `/ticket` subcommands.
- `/menu` command that auto-documents available bot commands.
- Conversations logged to PostgreSQL (`users`, `messages` tables) when a JDBC connection is provided.
- Free-form user messages proxied to OpenAI's Chat Completions API.
- In-memory ticket repository and session manager for local development with zero external dependencies.
- Structured SLF4J/Logback logging for chronological event tracing and debugging insight.

---

### Architecture
- **Main** (`Main.java`) bootstraps the Telegram bot using environment-provided secrets and obtains the JDBC connection.
- **Bot Layer** (`com.salex.telegram.Bot`) extends `TelegramLongPollingBot`, dispatches incoming updates, and orchestrates command handlers and ticket workflows.
- **Commanding** (`com.salex.telegram.Commanding`) defines the `CommandHandler` contract implemented by menu and ticket handlers.
- **Ticketing Domain** (`com.salex.telegram.Ticketing`) models tickets, priorities, statuses, repositories, and the `TicketService` that manages draft lifecycle and persistence.
- **Formatting** (`com.salex.telegram.Ticketing.commands.TicketMessageFormatter`) centralises Telegram-friendly message templates for consistency.

---

### Prerequisites
- **Java Development Kit:** JDK 23 (matching `maven.compiler.release`).
- **Apache Maven:** 3.9 or newer.
- **PostgreSQL:** Optional for persistence, but recommended for production runs.
- **Telegram Bot Token:** Register a bot via [@BotFather](https://core.telegram.org/bots#botfather).
- **OpenAI API Key:** Required for the conversational fallback handled in `TelegramBot#callChatGPT`.

---

### Environment Variables
Set the following before running the bot (shell snippet shown for reference):

```bash
export BOT_TOKEN="123456:ABCDEF"
export BOT_USERNAME="your_bot_username"
export JDBC_URL="jdbc:postgresql://localhost:5432/telegram_bot"
export DB_USER="bot_user"
export DB_PASS="super-secret-password"
export OPENAI_API_KEY="sk-..."          # Required for ChatGPT responses
```

`JDBC_URL`, `DB_USER`, and `DB_PASS` are optional—set them only when persisting users and messages. When any of them are missing, initialise the bot manually with a `null` connection to rely solely on in-memory ticket backing.

---

### Database Setup
The bot expects the following minimal schema when PostgreSQL logging is enabled:

```sql
CREATE TABLE IF NOT EXISTS users (
    id          BIGSERIAL PRIMARY KEY,
    telegram_id BIGINT UNIQUE NOT NULL,
    username    TEXT,
    first_name  TEXT,
    last_name   TEXT
);

CREATE TABLE IF NOT EXISTS messages (
    id       BIGSERIAL PRIMARY KEY,
    user_id  BIGINT NOT NULL REFERENCES users(id),
    chat_id  BIGINT NOT NULL,
    text     TEXT NOT NULL,
    reply    TEXT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT now()
);

CREATE TABLE IF NOT EXISTS ticket_sessions (
    chat_id   BIGINT NOT NULL,
    user_id   BIGINT NOT NULL,
    ticket_id BIGINT,
    summary   TEXT,
    priority  TEXT,
    details   TEXT,
    PRIMARY KEY (chat_id, user_id)
);
```

Run the DDL against the database pointed to by `JDBC_URL` before starting the bot.

---

### Build

```bash
mvn clean package
```

The command compiles the project and produces `target/telegram-bot-demo-1.0-SNAPSHOT.jar`.

---

### Run
Because the jar is built without shading dependencies, copy them locally and launch the bot on the runtime classpath:

```bash
mvn dependency:copy-dependencies
java -cp target/telegram-bot-demo-1.0-SNAPSHOT.jar:target/dependency/* Main
```

> **Windows note:** replace the classpath colon (`:`) with a semicolon (`;`).

Running from an IDE is equally valid—ensure the required environment variables are defined in your run configuration.

---

### Bot Commands
- `/menu` – Lists every registered command alongside its description.
- `/ticket new` – Starts an interactive ticket creation session.
- `/ticket list` – Displays all tickets created by the user.
- `/ticket <id>` – Shows a detailed summary card for the referenced ticket.
- `/ticket close <id> <note>` – Closes the ticket and appends the resolution note.
- `/ticket help` – Echoes helpful usage content for all ticket subcommands.

Any message that does not begin with `/` is forwarded to the OpenAI Chat Completions API and the response is echoed back to the user.

---

### Ticket Workflow
1. **Start:** `/ticket new` creates a draft ticket (stored in PostgreSQL via the repository and tracked in-memory by `TicketSessionManager`).
2. **Prompting:** The bot sequentially asks for summary, priority, and detailed description, persisting each answer.
3. **Completion:** When all required fields are provided, the draft session is closed and a final ticket card is shared with the user.
4. **Management:** `/ticket list`, `/ticket <id>`, and `/ticket close` allow users to view or close tickets they created or are assigned to (assignment support is available for future enhancements).

---

### Debugging & Troubleshooting
- **Connectivity issues:** Confirm `BOT_TOKEN`, `BOT_USERNAME`, and network connectivity to Telegram servers. A successful registration is logged at `INFO` level.
- **Database errors:** Ensure the database is reachable and the schema above is applied. Any SQL exception is surfaced back to the user as an `[Error]` message.
- **OpenAI failures:** Verify `OPENAI_API_KEY` and outbound network access to `api.openai.com`. Failures return `[Error] Failed to process message: ...` inside the chat.
- **Structured logging:** SLF4J with Logback is configured (`src/main/resources/logback.xml`). Adjust levels or patterns there, or set `LOG_LEVEL_ROOT` at runtime to increase verbosity when diagnosing issues.

---

### Testing

```bash
mvn test
```

JUnit 5 and AssertJ are available for both unit and integration testing of command handlers, services, and repositories.

---

### Extending the Bot
- **Persisting tickets:** Replace `InMemoryTicketRepository` with a JDBC-backed repository implementation and inject it into `TelegramBot` at construction time.
- **Session management:** Swap `InMemoryTicketSessionManager` for a Redis or database-backed manager to support multi-instance deployments.
- **New commands:** Implement a `CommandHandler`, expose it via `TelegramBotModule#getCommands`, and include the module in the bot so the command surfaces automatically in `/menu`.
- **Alternative LLM providers:** Replace `callChatGPT` with your preferred HTTP integration while keeping the conversational flow unchanged.

---

### Project Structure
```
src/
  main/java/
    Main.java                               # Application entry point
    com/salex/telegram/Bot/                 # Telegram bot implementation and command registration
    com/salex/telegram/commanding/          # CommandHandler interface contract
    com/salex/telegram/ticketing/           # Ticket domain models, services, repositories
    com/salex/telegram/ticketing/commands/  # Ticket command handler and message formatting
```

---

The README should give you everything needed to configure secrets, build the project, and operate the bot across local and production-like environments. Happy shipping!
