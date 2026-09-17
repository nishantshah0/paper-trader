package io.github.nishantshah0.papertrader.portfolio;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/accounts/{accountId}/portfolio")
class PortfolioController {

    private final PortfolioService portfolios;

    PortfolioController(PortfolioService portfolios) {
        this.portfolios = portfolios;
    }

    @GetMapping
    PortfolioResponse get(@PathVariable Long accountId) {
        return portfolios.portfolio(accountId);
    }
}
