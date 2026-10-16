package io.github.nishantshah0.papertrader;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.nishantshah0.papertrader.account.Account;
import io.github.nishantshah0.papertrader.account.AccountRepository;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

class AccountVersionTest extends ApiTestSupport {

    @Autowired AccountRepository accounts;

    @Test
    void aStaleBalanceUpdateIsRejectedInsteadOfOverwritingCash() throws Exception {
        long id = createAccount().id();
        Account first = accounts.findById(id).orElseThrow();
        Account stale = accounts.findById(id).orElseThrow();

        first.debit(new BigDecimal("100.00"));
        accounts.saveAndFlush(first);

        stale.debit(new BigDecimal("50.00"));
        assertThatThrownBy(() -> accounts.saveAndFlush(stale))
                .isInstanceOf(ObjectOptimisticLockingFailureException.class);
        assertThat(accounts.findById(id).orElseThrow().getCash()).isEqualByComparingTo("900.00");
    }
}
