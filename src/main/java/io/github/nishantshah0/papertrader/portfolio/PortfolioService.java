package io.github.nishantshah0.papertrader.portfolio;

import io.github.nishantshah0.papertrader.account.Account;
import io.github.nishantshah0.papertrader.account.AccountService;
import io.github.nishantshah0.papertrader.portfolio.PortfolioResponse.PositionView;
import io.github.nishantshah0.papertrader.quote.Quote;
import io.github.nishantshah0.papertrader.quote.QuoteCache;
import io.github.nishantshah0.papertrader.trading.Position;
import io.github.nishantshah0.papertrader.trading.PositionRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PortfolioService {

    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

    private final AccountService accounts;
    private final PositionRepository positions;
    private final QuoteCache quotes;

    PortfolioService(AccountService accounts, PositionRepository positions, QuoteCache quotes) {
        this.accounts = accounts;
        this.positions = positions;
        this.quotes = quotes;
    }

    @Transactional(readOnly = true)
    public PortfolioResponse portfolio(Long accountId) {
        Account account = accounts.get(accountId);
        List<PositionView> views = new ArrayList<>();
        BigDecimal marketValue = ZERO;
        BigDecimal unrealized = ZERO;
        BigDecimal realized = ZERO;
        for (Position position : positions.findByAccountIdOrderBySymbol(accountId)) {
            realized = realized.add(position.getRealizedPnl());
            if (position.getQuantity() == 0) {
                continue;
            }
            // a symbol dropped from config still needs a mark; fall back to cost
            BigDecimal last = quotes.get(position.getSymbol()).map(Quote::price).orElse(position.getAvgCost());
            BigDecimal value = position.marketValue(last);
            BigDecimal pnl = position.unrealizedPnl(last);
            marketValue = marketValue.add(value);
            unrealized = unrealized.add(pnl);
            views.add(new PositionView(position.getSymbol(), position.getQuantity(), position.getAvgCost(), last,
                    value, pnl, position.getRealizedPnl()));
        }
        BigDecimal totalValue = account.getCash().add(marketValue);
        return new PortfolioResponse(account.getId(), account.getUser().getUsername(), account.getCash(),
                account.getStartingCash(), marketValue, totalValue, unrealized, realized,
                totalValue.subtract(account.getStartingCash()), views);
    }
}
