package io.github.nishantshah0.papertrader.trading;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "papertrader.engine.enabled", havingValue = "true", matchIfMissing = true)
public class MatchingEngine {
    private static final Logger log = LoggerFactory.getLogger(MatchingEngine.class);
    private final OrderRepository orders;
    private final OrderService service;

    MatchingEngine(OrderRepository orders, OrderService service) {
        this.orders = orders;
        this.service = service;
    }

    // Postgres is the durable book. Reloading each tick also recovers orders after
    // restart, without a second source of truth or a cache/commit race.
    @Scheduled(fixedDelayString = "${papertrader.engine.interval-ms:1000}")
    public void tick() {
        for (Order order : orders.findByStatusOrderByCreatedAtAscIdAsc(OrderStatus.OPEN)) {
            try {
                service.match(order.getAccountId(), order.getId());
            } catch (RuntimeException e) {
                log.warn("Order {} could not be matched; retrying next tick ({})", order.getId(), e.getClass().getSimpleName());
            }
        }
    }
}
