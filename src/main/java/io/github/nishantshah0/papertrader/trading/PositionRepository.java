package io.github.nishantshah0.papertrader.trading;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PositionRepository extends JpaRepository<Position, Long> {

    Optional<Position> findByAccountIdAndSymbol(Long accountId, String symbol);

    List<Position> findByAccountIdOrderBySymbol(Long accountId);
}
