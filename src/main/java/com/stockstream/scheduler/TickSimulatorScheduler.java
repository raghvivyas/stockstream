package com.stockstream.scheduler;

import com.stockstream.config.AppProperties;
import com.stockstream.kafka.TickProducer;
import com.stockstream.model.Tick;
import com.stockstream.service.MockTickGeneratorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Simulates an NSE market feed by generating and publishing ticks to Kafka
 * at a configurable interval (default: every 500 ms).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TickSimulatorScheduler {

    private final MockTickGeneratorService generator;
    private final TickProducer             producer;
    private final AppProperties            props;

    @Scheduled(fixedDelayString = "${stockstream.tick-interval-ms:500}")
    public void simulateTick() {
        for (String symbol : props.getSymbols()) {
            try {
                Tick tick = generator.generateTick(symbol);
                producer.publish(tick);
            } catch (Exception e) {
                log.error("Error simulating tick for {}: {}", symbol, e.getMessage());
            }
        }
    }
}
