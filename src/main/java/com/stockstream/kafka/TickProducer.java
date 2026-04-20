package com.stockstream.kafka;

import com.stockstream.model.Tick;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;
import org.springframework.util.concurrent.ListenableFutureCallback;

@Slf4j
@Component
@RequiredArgsConstructor
public class TickProducer {

    private static final String TOPIC = "market-ticks";

    private final KafkaTemplate<String, Tick> kafkaTemplate;

    /**
     * Publishes a tick to Kafka. Uses symbol as the message key so that
     * all ticks for the same symbol land on the same partition, preserving order.
     */
    public void publish(Tick tick) {
        kafkaTemplate.send(TOPIC, tick.getSymbol(), tick)
                .addCallback(new ListenableFutureCallback<SendResult<String, Tick>>() {
                    @Override
                    public void onSuccess(SendResult<String, Tick> result) {
                        log.debug("Tick published: symbol={} partition={} offset={}",
                                tick.getSymbol(),
                                result.getRecordMetadata().partition(),
                                result.getRecordMetadata().offset());
                    }

                    @Override
                    public void onFailure(Throwable ex) {
                        log.error("Failed to publish tick for symbol={}: {}",
                                tick.getSymbol(), ex.getMessage());
                    }
                });
    }
}
