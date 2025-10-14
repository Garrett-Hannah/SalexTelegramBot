import com.salex.telegram.Bot.SalexTelegramBot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * Application entry point that wires the Telegram bot and database connection.
 */
public class Main {
    private static final Logger log = LoggerFactory.getLogger(Main.class);

    /**
     * Boots the Telegram bot with credentials and JDBC connection sourced from environment variables.
     *
     * @param args CLI arguments (unused)
     * @throws Exception if the bot initialisation or database connection fails
     */
    public static void main(String[] args) throws Exception {
        String token = System.getenv("BOT_TOKEN");
        String username = System.getenv("BOT_USERNAME");

        Connection conn = null;
        try {
            conn = DriverManager.getConnection(
                    System.getenv("JDBC_URL"),
                    System.getenv("DB_USER"),
                    System.getenv("DB_PASS")
            );
            log.info("Database connection established for bot startup");
        } catch (SQLException ex) {
            log.warn("Failed to establish database connection; bot will run with in-memory storage: {}", ex.getMessage());
        }

        TelegramBotsApi botsApi = new TelegramBotsApi(DefaultBotSession.class);
        botsApi.registerBot(new SalexTelegramBot(token, username, conn));
        log.info("Bot started using username {}", username);
    }
}
