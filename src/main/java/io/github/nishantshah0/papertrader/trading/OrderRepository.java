package io.github.nishantshah0.papertrader.trading;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderRepository extends JpaRepository<Order, Long> {

    Optional<Order> findByAccountIdAndIdempotencyKey(Long accountId, String idempotencyKey);

    List<Order> findByStatusOrderByCreatedAtAscIdAsc(OrderStatus status);

    List<Order> findByAccountIdOrderByCreatedAtDesc(Long accountId);

    List<Order> findByAccountIdAndStatusOrderByCreatedAtDesc(Long accountId, OrderStatus status);

    Optional<Order> findByIdAndAccountId(Long id, Long accountId);
}
