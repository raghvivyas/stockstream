package com.stockstream.service;

import com.stockstream.entity.CandleEntity;
import com.stockstream.model.Tick;
import com.stockstream.repository.CandleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class CandleAggregatorServiceTest {

    @Mock
    private CandleRepository candleRepository;

    @InjectMocks
    private CandleAggregatorService service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void firstTickForSymbol_startsNewCandle_doesNotPersist() {
        Tick tick = buildTick("RELIANCE", "2850.00", Instant.now());
        service.processTick(tick);
        verify(candleRepository, never()).save(any(CandleEntity.class));
    }

    @Test
    void tickInNewMinute_finalizesOldCandle_andPersists() {
        Instant minute1 = Instant.now().truncatedTo(ChronoUnit.MINUTES);
        Instant minute2 = minute1.plus(1, ChronoUnit.MINUTES);

        service.processTick(buildTick("TCS", "3540.00", minute1.plusSeconds(5)));
        service.processTick(buildTick("TCS", "3545.00", minute1.plusSeconds(30)));
        service.processTick(buildTick("TCS", "3548.00", minute2.plusSeconds(5)));

        verify(candleRepository, times(1)).save(any(CandleEntity.class));
    }

    @Test
    void multipleTicksSameMinute_onlyUpdatesInMemory() {
        Instant now = Instant.now().truncatedTo(ChronoUnit.MINUTES).plusSeconds(10);
        service.processTick(buildTick("INFY", "1480.00", now));
        service.processTick(buildTick("INFY", "1485.00", now.plusSeconds(10)));
        service.processTick(buildTick("INFY", "1478.00", now.plusSeconds(20)));
        verify(candleRepository, never()).save(any(CandleEntity.class));
    }

    private Tick buildTick(String symbol, String price, Instant ts) {
        return Tick.builder()
                .symbol(symbol)
                .price(new BigDecimal(price))
                .open(new BigDecimal(price))
                .high(new BigDecimal(price))
                .low(new BigDecimal(price))
                .volume(1000L)
                .timestamp(ts)
                .build();
    }
}
