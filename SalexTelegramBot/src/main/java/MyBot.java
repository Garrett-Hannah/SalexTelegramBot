import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.net.URI;
import java.net.http.*;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

public class MyBot extends TelegramLongPollingBot {
    private final String username;
    private final Connection conn;

    public MyBot(String token, String username, Connection conn) {
        super(token);
        this.username = username;
        this.conn = conn;
    }

    @Override
    public String getBotUsername() {
        return username;
    }

    @Override
    public void onUpdateReceived(Update update) {
        if (update.hasMessage() && update.getMessage().hasText()) {
            String userText = update.getMessage().getText();
            Long chatId = update.getMessage().getChatId();
            Long telegramId = update.getMessage().getFrom().getId();

            try {
                // 1. Ensure user exists (insert if not found)
                PreparedStatement findUser = conn.prepareStatement(
                        "SELECT id FROM users WHERE telegram_id=?");
                findUser.setLong(1, telegramId);
                ResultSet rs = findUser.executeQuery();

                long userId;
                if (rs.next()) {
                    // user already in table
                    userId = rs.getLong("id");
                } else {
                    // insert new user and get generated id
                    PreparedStatement insertUser = conn.prepareStatement(
                            "INSERT INTO users (telegram_id, username, first_name, last_name) " +
                                    "VALUES (?,?,?,?) RETURNING id");
                    insertUser.setLong(1, telegramId);
                    insertUser.setString(2, update.getMessage().getFrom().getUserName());
                    insertUser.setString(3, update.getMessage().getFrom().getFirstName());
                    insertUser.setString(4, update.getMessage().getFrom().getLastName());
                    ResultSet newUser = insertUser.executeQuery();
                    newUser.next();
                    userId = newUser.getLong("id");
                }

                // 2. Call GPT (stub here, replace with real call)
                String replyText = callChatGPT(userText);

                // 3. Insert the message with internal userId
                PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO messages (user_id, chat_id, text, reply) VALUES (?,?,?,?)");
                ps.setLong(1, userId);        // internal DB id
                ps.setLong(2, chatId);
                ps.setString(3, userText);
                ps.setString(4, replyText);
                ps.executeUpdate();

                // 4. Send reply back to Telegram
                SendMessage message = new SendMessage(chatId.toString(), replyText);
                execute(message);

            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }


    private String callChatGPT(String prompt) throws Exception {
        HttpClient client = HttpClient.newHttpClient();
        String body = """
        {
          "model": "gpt-4o-mini",
          "messages": [{"role":"user","content":"%s"}]
        }
        """.formatted(prompt);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://api.openai.com/v1/chat/completions"))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + System.getenv("OPENAI_API_KEY"))
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        return response.body(); // TODO: parse with Gson for just the text
    }
}
