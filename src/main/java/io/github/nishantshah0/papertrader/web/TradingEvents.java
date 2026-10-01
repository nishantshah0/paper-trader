package io.github.nishantshah0.papertrader.web;
import io.github.nishantshah0.papertrader.quote.Quote;
import io.github.nishantshah0.papertrader.trading.OrderChanged;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
class TradingEvents {
    private final SimpMessagingTemplate messaging;
    TradingEvents(SimpMessagingTemplate messaging) { this.messaging = messaging; }
    @EventListener public void quote(Quote quote) { messaging.convertAndSend("/topic/quotes", quote); }
    @TransactionalEventListener public void order(OrderChanged event) {
        messaging.convertAndSend("/topic/accounts/" + event.accountId(), event);
    }
}
