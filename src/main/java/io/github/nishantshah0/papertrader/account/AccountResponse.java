package io.github.nishantshah0.papertrader.account;

import java.math.BigDecimal;
import java.time.Instant;

public record AccountResponse(Long id, String username, BigDecimal cash, BigDecimal startingCash, Instant createdAt) {

    public static AccountResponse from(Account account) {
        return new AccountResponse(account.getId(), account.getUser().getUsername(), account.getCash(),
                account.getStartingCash(), account.getCreatedAt());
    }
}
