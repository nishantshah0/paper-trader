package io.github.nishantshah0.papertrader.quote;

import java.util.List;
import java.util.Locale;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/quotes")
class QuoteController {

    private final QuoteCache quotes;

    QuoteController(QuoteCache quotes) {
        this.quotes = quotes;
    }

    @GetMapping
    List<Quote> all() {
        return quotes.all();
    }

    @GetMapping("/{symbol}")
    Quote one(@PathVariable String symbol) {
        return quotes.require(symbol.toUpperCase(Locale.ROOT));
    }
}
