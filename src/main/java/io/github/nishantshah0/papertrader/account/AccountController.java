package io.github.nishantshah0.papertrader.account;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/accounts")
class AccountController {

    private final AccountService accounts;

    AccountController(AccountService accounts) {
        this.accounts = accounts;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    AccountResponse create(@Valid @RequestBody CreateAccountRequest request) {
        return AccountResponse.from(accounts.create(request.username()));
    }

    @GetMapping("/{id}")
    AccountResponse get(@PathVariable Long id) {
        return AccountResponse.from(accounts.get(id));
    }
}
