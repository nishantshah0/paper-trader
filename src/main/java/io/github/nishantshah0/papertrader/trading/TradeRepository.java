package io.github.nishantshah0.papertrader.trading;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TradeRepository extends JpaRepository<Trade, Long> {

    List<Trade> findByAccountIdOrderByExecutedAtDesc(Long accountId);
}
