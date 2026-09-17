package io.github.nishantshah0.papertrader.account;

import io.github.nishantshah0.papertrader.NotFoundException;
import io.github.nishantshah0.papertrader.PaperTraderProperties;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountService {

    private final UserRepository users;
    private final AccountRepository accounts;
    private final PaperTraderProperties properties;

    AccountService(UserRepository users, AccountRepository accounts, PaperTraderProperties properties) {
        this.users = users;
        this.accounts = accounts;
        this.properties = properties;
    }

    @Transactional
    public Account create(String username) {
        if (users.existsByUsername(username)) {
            throw new UsernameTakenException(username);
        }
        User user = users.save(new User(username));
        return accounts.save(new Account(user, properties.startingCash()));
    }

    @Transactional(readOnly = true)
    public Account get(Long id) {
        return accounts.findById(id).orElseThrow(() -> new NotFoundException("account " + id + " not found"));
    }
}
