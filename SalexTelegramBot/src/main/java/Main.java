import com.salex.telegram.Bot.TelegramBot;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;
import java.sql.Connection;
import java.sql.DriverManager;

/**
 * Application entry point that wires the Telegram bot and database connection.
 */
public class Main {
    /**
     * Boots the Telegram bot with credentials and JDBC connection sourced from environment variables.
     *
     * @param args CLI arguments (unused)
     * @throws Exception if the bot initialisation or database connection fails
     */
    public static void main(String[] args) throws Exception {
        String token = System.getenv("BOT_TOKEN");
        String username = System.getenv("BOT_USERNAME");

        // connect to Postgres
        Connection conn = DriverManager.getConnection(
                System.getenv("JDBC_URL"),
                System.getenv("DB_USER"),
                System.getenv("DB_PASS")
        );

        TelegramBotsApi botsApi = new TelegramBotsApi(DefaultBotSession.class);
        botsApi.registerBot(new TelegramBot(token, username, conn));
        System.out.println("Bot started...");
    }
}
