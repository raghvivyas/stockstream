package com.stockstream.service;

import com.stockstream.config.AppProperties;
import com.stockstream.model.Tick;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

/**
 * Simulates NSE market tick data using a random-walk model.
 * Each tick moves ±0.5% from the previous price, mimicking real intraday volatility.
 */
@Slf4j
@Service
public class MockTickGeneratorService {

    private static final Map<String, BigDecimal> BASE_PRICES = new HashMap<>();

    static {
        BASE_PRICES.put("RELIANCE",   new BigDecimal("2850.00"));
        BASE_PRICES.put("TCS",        new BigDecimal("3540.00"));
        BASE_PRICES.put("INFY",       new BigDecimal("1480.00"));
        BASE_PRICES.put("HDFCBANK",   new BigDecimal("1620.00"));
        BASE_PRICES.put("WIPRO",      new BigDecimal("455.00"));
        BASE_PRICES.put("ICICIBANK",  new BigDecimal("1010.00"));
        BASE_PRICES.put("SBIN",       new BigDecimal("605.00"));
        BASE_PRICES.put("BAJFINANCE", new BigDecimal("6520.00"));
    }

    private final Map<String, BigDecimal> currentPrices = new HashMap<>(BASE_PRICES);
    private final Map<String, BigDecimal> openPrices    = new HashMap<>(BASE_PRICES);
    private final Map<String, BigDecimal> sessionHighs  = new HashMap<>(BASE_PRICES);
    private final Map<String, BigDecimal> sessionLows   = new HashMap<>(BASE_PRICES);

    private final AppProperties props;
    private final Random random = new Random();

    public MockTickGeneratorService(AppProperties props) {
        this.props = props;
    }

    /**
     * Generates a single tick for the given symbol using a random-walk price model.
     */
    public Tick generateTick(String symbol) {
        BigDecimal lastPrice = currentPrices.getOrDefault(symbol, new BigDecimal("1000.00"));

        // Random walk: ±0.3% change per tick
        double changePct = (random.nextDouble() - 0.5) * 0.006;
        BigDecimal newPrice = lastPrice
                .multiply(BigDecimal.ONE.add(new BigDecimal(changePct)))
                .setScale(2, RoundingMode.HALF_UP);

        // Update session tracking
        currentPrices.put(symbol, newPrice);
        sessionHighs.merge(symbol, newPrice, BigDecimal::max);
        sessionLows.merge(symbol, newPrice, BigDecimal::min);

        long volume = 100L + (long) (random.nextDouble() * 9900);

        return Tick.builder()
                .symbol(symbol)
                .price(newPrice)
                .open(openPrices.getOrDefault(symbol, newPrice))
                .high(sessionHighs.getOrDefault(symbol, newPrice))
                .low(sessionLows.getOrDefault(symbol, newPrice))
                .volume(volume)
                .timestamp(Instant.now())
                .build();
    }

    /** Returns the current tracked price for a symbol (used by summary endpoint). */
    public BigDecimal getCurrentPrice(String symbol) {
        return currentPrices.getOrDefault(symbol, BigDecimal.ZERO);
    }

    /** Returns all symbols that the generator tracks. */
    public java.util.List<String> getSymbols() {
        return props.getSymbols();
    }
}
