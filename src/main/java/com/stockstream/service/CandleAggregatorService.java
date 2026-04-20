package com.stockstream.service;

import com.stockstream.entity.CandleEntity;
import com.stockstream.model.Tick;
import com.stockstream.repository.CandleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Aggregates incoming ticks into 1-minute OHLCV candles.
 *
 * Design: uses ConcurrentHashMap#compute for atomic, lock-free updates.
 * When a tick arrives for a new minute bucket, the previous candle is finalized
 * and persisted to PostgreSQL outside the compute block to avoid blocking.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CandleAggregatorService {

    private final CandleRepository candleRepository;

    /** In-memory state: one InProgressCandle per symbol. */
    private final ConcurrentHashMap<String, InProgressCandle> inProgress = new ConcurrentHashMap<>();

    public void processTick(Tick tick) {
        Instant minuteBucket = tick.getTimestamp().truncatedTo(ChronoUnit.MINUTES);
        AtomicReference<CandleEntity> toFinalize = new AtomicReference<>();

        inProgress.compute(tick.getSymbol(), (symbol, current) -> {
            if (current == null) {
                return InProgressCandle.from(tick, minuteBucket);
            }
            if (!current.getMinuteBucket().equals(minuteBucket)) {
                // New minute: capture the completed candle, start fresh
                toFinalize.set(current.toEntity());
                return InProgressCandle.from(tick, minuteBucket);
            }
            current.update(tick);
            return current;
        });

        // Persist outside compute() to avoid blocking other threads on the same key
        if (toFinalize.get() != null) {
            try {
                candleRepository.save(toFinalize.get());
                log.debug("Candle finalized: symbol={} close={} volume={}",
                        toFinalize.get().getSymbol(),
                        toFinalize.get().getClose(),
                        toFinalize.get().getVolume());
            } catch (Exception e) {
                log.error("Failed to persist candle: {}", e.getMessage());
            }
        }
    }

    // ── Inner class ────────────────────────────────────────────────────────────

    private static class InProgressCandle {
        private String symbol;
        private BigDecimal open;
        private BigDecimal high;
        private BigDecimal low;
        private BigDecimal close;
        private long volume;
        private Instant minuteBucket;

        static InProgressCandle from(Tick tick, Instant bucket) {
            InProgressCandle c = new InProgressCandle();
            c.symbol       = tick.getSymbol();
            c.open         = tick.getPrice();
            c.high         = tick.getPrice();
            c.low          = tick.getPrice();
            c.close        = tick.getPrice();
            c.volume       = tick.getVolume() != null ? tick.getVolume() : 0L;
            c.minuteBucket = bucket;
            return c;
        }

        void update(Tick tick) {
            if (tick.getPrice().compareTo(this.high) > 0) this.high = tick.getPrice();
            if (tick.getPrice().compareTo(this.low)  < 0) this.low  = tick.getPrice();
            this.close   = tick.getPrice();
            this.volume += tick.getVolume() != null ? tick.getVolume() : 0L;
        }

        Instant getMinuteBucket() { return minuteBucket; }

        CandleEntity toEntity() {
            return CandleEntity.builder()
                    .symbol(symbol)
                    .open(open)
                    .high(high)
                    .low(low)
                    .close(close)
                    .volume(volume)
                    .openTime(minuteBucket)
                    .closeTime(minuteBucket.plus(1, ChronoUnit.MINUTES).minusMillis(1))
                    .build();
        }
    }
}
