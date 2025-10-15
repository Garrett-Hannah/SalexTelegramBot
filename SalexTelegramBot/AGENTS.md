# Repository Guidelines

## Project Structure & Module Organization
Source lives under `src/main/java`, with entrypoint `Main.java` orchestrating bot startup and database wiring. Core bot logic is grouped by concern: `com/salex/telegram/Bot` handles Telegram session plumbing, `commanding` encapsulates command dispatch, and `ticketing` keeps ticket workflows and persistence abstractions. AI integrations sit in `AIpackage` for future expansion. Tests belong in `src/test/java`; mirror the production package path so collaborators can navigate easily.

## Build, Test, and Development Commands
- `mvn clean package` rebuilds the project, resolving dependencies and generating an executable JAR in `target/`.
- `mvn test` runs the JUnit 5 suite with Surefire; use it before pushing changes.
- `mvn exec:java -Dexec.mainClass=Main` runs the bot locally once environment variables are exported.

## Coding Style & Naming Conventions
Stick to Java 23 features approved by the team; prefer 4-space indentation and braces on the same line. Keep package names lowercase (`com.salex.telegram.*`), classes in PascalCase, and methods/fields in camelCase. Favor descriptive ticketing terminology (e.g., `TicketDraft`, `TicketPriority`) and co-locate helpers inside the relevant package. Run an auto-formatter such as IntelliJ’s “Reformat Code” or `mvn fmt:format` if configured to avoid style noise in reviews.

## Testing Guidelines
Write unit tests with JUnit 5 and AssertJ (already declared in the POM). Name test classes `<TypeName>Test` and focus on service-level behavior (`TicketServiceTest`, `MenuCommandHandlerTest`). Stub database access with the in-memory repository interfaces to keep tests fast. Maintain coverage by validating new command handlers and regression cases before shipping.

## Commit & Pull Request Guidelines
Existing history favors short, sentence-style summaries; continue using concise, present-tense subject lines (e.g., `Improve ticket session cleanup`). Group related changes, reference issues when available, and include a one-paragraph PR description covering motivation, approach, and manual verification. Attach screenshots or terminal excerpts for user-facing changes. Ensure CI or local `mvn test` passes before requesting review.

## Configuration & Secrets
The bot expects `BOT_TOKEN`, `BOT_USERNAME`, `JDBC_URL`, `DB_USER`, and `DB_PASS` to be present in the environment. Use a local `.env` file and a loader (or `export` commands) for development, but never commit secrets. When testing with Postgres, point `JDBC_URL` at a disposable schema and clean up test data after runs.
