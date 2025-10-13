import com.salex.telegram.Bot.TelegramBot;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;
import java.sql.Connection;
import java.sql.DriverManager;

public class Main {
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
