package com.salex.telegram.application.modules.ticketing;

import com.salex.telegram.application.modules.CommandHandler;
import com.salex.telegram.application.modules.UpdateHandlingService;
import com.salex.telegram.telegram.SalexTelegramBot;
import com.salex.telegram.ticketing.application.TicketService;
import com.salex.telegram.ticketing.domain.Ticket;
import com.salex.telegram.ticketing.domain.TicketDraft;
import com.salex.telegram.ticketing.presentation.TicketCommandHandler;
import com.salex.telegram.ticketing.presentation.TicketMessageFormatter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;

import java.util.Map;
import java.util.Optional;

/**
 * Continues multi-step ticket drafts by consuming free-form replies whenever a draft session is active.
 * Delegates persistence and validation to {@link TicketService} and surfaces user feedback via
 * {@link TicketMessageFormatter}.
 */
@Service
public class TicketingHandlingService implements UpdateHandlingService {
    private static final Logger log = LoggerFactory.getLogger(TicketingHandlingService.class);

    @Autowired
    private TicketService ticketService;
    @Autowired
    private TicketMessageFormatter formatter;
    @Autowired
    private TicketCommandHandler ticketCommandHandler;

    @Override
    public boolean canHandle(Update update, long userId) {
        if (update == null || !update.hasMessage()) {
            return false;
        }
        Message message = update.getMessage();
        if (!message.hasText()) {
            return false;
        }
        long chatId = message.getChatId();
        return ticketService.hasActiveDraft(chatId, userId);
    }

    @Override
    public void handle(Update update, SalexTelegramBot bot, long userId) {
        if (update == null || !update.hasMessage()) {
            return;
        }
        Message message = update.getMessage();
        long chatId = message.getChatId();
        Integer threadId = message.getMessageThreadId();
        String messageText = message.getText() != null ? message.getText().trim() : "";

        Optional<TicketDraft.Step> currentStep = ticketService.getActiveStep(chatId, userId);
        if (currentStep.isEmpty()) {
            bot.sendMessage(chatId, threadId, formatter.formatError("No active ticket step found."));
            log.warn("No active ticket step found for chat {}, user {}", chatId, userId);
            return;
        }

        try {
            Ticket ticket = ticketService.collectTicketField(chatId, userId, messageText);
            log.info("Collected ticket field at step {} for ticket {}", currentStep.get(), ticket.getId());
            bot.sendMessage(chatId, threadId, formatter.formatStepAcknowledgement(currentStep.get(), ticket));

            Optional<TicketDraft.Step> nextStep = ticketService.getActiveStep(chatId, userId);
            if (nextStep.isPresent()) {
                log.debug("Next ticket step for ticket {} is {}", ticket.getId(), nextStep.get());
                bot.sendMessage(chatId, threadId, formatter.formatNextStepPrompt(nextStep.get()));
            } else {
                log.info("Ticket {} creation complete", ticket.getId());
                bot.sendMessage(chatId, threadId, formatter.formatCreationComplete(ticket));
            }
        } catch (IllegalArgumentException | IllegalStateException ex) {
            bot.sendMessage(chatId, threadId, formatter.formatError(ex.getMessage()));
            log.error("Failed to collect ticket field for chat {}, user {}: {}", chatId, userId, ex.getMessage(), ex);
        }
    }
}
