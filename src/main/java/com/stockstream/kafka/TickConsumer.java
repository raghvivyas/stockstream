package com.stockstream.kafka;

import com.stockstream.model.Tick;
import com.stockstream.service.CandleAggregatorService;
import com.stockstream.service.RedisTickCacheService;
import com.stockstream.websocket.WebSocketBroadcaster;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class TickConsumer {

    private final CandleAggregatorService candleAggregatorService;
    private final RedisTickCacheService redisTickCacheService;
    private final WebSocketBroadcaster webSocketBroadcaster;

    @KafkaListener(
            topics = "market-ticks",
            groupId = "stockstream-group",
            containerFactory = "tickKafkaListenerContainerFactory"
    )
    public void consume(
            @Payload Tick tick,
            @Header(KafkaHeaders.RECEIVED_PARTITION_ID) int partition,
            @Header(KafkaHeaders.OFFSET) long offset) {

        log.debug("Consumed tick: symbol={} price={} partition={} offset={}",
                tick.getSymbol(), tick.getPrice(), partition, offset);

        try {
            // 1. Aggregate into 1-min candles and persist finalized candles
            candleAggregatorService.processTick(tick);

            // 2. Cache in Redis sorted set (last N ticks per symbol)
            redisTickCacheService.addTick(tick);

            // 3. Broadcast live tick to WebSocket subscribers
            webSocketBroadcaster.broadcastTick(tick);

        } catch (Exception e) {
            log.error("Error processing tick symbol={}: {}", tick.getSymbol(), e.getMessage(), e);
        }
    }
}
