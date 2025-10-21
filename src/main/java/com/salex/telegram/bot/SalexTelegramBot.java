package com.salex.telegram.bot;

import com.salex.telegram.config.TelegramBotProperties;
import com.salex.telegram.modules.CommandHandler;
import com.salex.telegram.users.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.objects.Update;

import java.util.Map;

/**
 * Spring-managed Telegram bot that routes updates through registered modules.
 */
@Component
public class SalexTelegramBot extends TelegramLongPollingBot {
    private static final Logger log = LoggerFactory.getLogger(SalexTelegramBot.class);

    private final String username;
    //TODO: turn command into a component to interact with the spring project.
    private final Map<String, CommandHandler> commands;
    private final UserService userService;
    private final TelegramSender telegramSender;
    private final UpdateRouter updateRouter;

    //Need to find if i can set up a alternate way of this. maybe maybe not idk,
    public SalexTelegramBot(TelegramBotProperties properties,
                            Map<String, CommandHandler> commands,
                            UserService userService) {
        super(properties.getToken());
        this.username = properties.getUsername();
        this.commands = commands;
        this.userService = userService;
        this.telegramSender = new TelegramSender(this);
        this.updateRouter = new UpdateRouter(commands, userService, telegramSender);

        log.info("TelegramBot registered {} command handlers", commands.size());
    }

    @Override
    public String getBotUsername() {
        return username;
    }

    @Override
    public void onUpdateReceived(Update update) {
        updateRouter.route(update, this);
    }

    public void sendMessage(long chatId, String text) {
        sendMessage(chatId, null, text);
    }

    //TODO: also change this. I think it could maybe be within a better defined
    //Chat component rather than this message function.
    public void sendMessage(long chatId, Integer threadId, String text) {
        telegramSender.sendMessage(chatId, threadId, text);
    }

    //TODO: optional, maybe change where stuff like this goes.
    public void sendChatTyping(long chatId, Integer threadId) {
        telegramSender.sendChatTyping(chatId, threadId);
    }
}
