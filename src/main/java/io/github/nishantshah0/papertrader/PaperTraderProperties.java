package io.github.nishantshah0.papertrader;

import java.math.BigDecimal;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("papertrader")
public record PaperTraderProperties(BigDecimal startingCash, Map<String, BigDecimal> seedQuotes) {
}
