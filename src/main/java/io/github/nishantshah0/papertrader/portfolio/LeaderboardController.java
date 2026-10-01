package io.github.nishantshah0.papertrader.portfolio;
import io.github.nishantshah0.papertrader.account.AccountRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.List;
import org.springframework.web.bind.annotation.*;
import org.springframework.transaction.annotation.Transactional;

@RestController
class LeaderboardController {
    private final AccountRepository accounts;
    private final PortfolioService portfolios;
    LeaderboardController(AccountRepository accounts, PortfolioService portfolios) {
        this.accounts = accounts; this.portfolios = portfolios;
    }
    public record Entry(Long accountId, String username, BigDecimal totalValue, BigDecimal returnPercent) {}
    @GetMapping("/api/leaderboard")
    @Transactional(readOnly = true, isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public List<Entry> leaderboard() {
        return accounts.findAll().stream().map(a -> portfolios.portfolio(a.getId()))
                .map(p -> new Entry(p.accountId(), p.username(), p.totalValue(),
                        p.totalPnl().multiply(new BigDecimal("100")).divide(p.startingCash(), 2, RoundingMode.HALF_UP)))
                .sorted(Comparator.comparing(Entry::returnPercent).reversed().thenComparing(Entry::accountId))
                .limit(20).toList();
    }
}
