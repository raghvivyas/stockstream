package com.stockstream.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stockstream.config.AppProperties;
import com.stockstream.model.Tick;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * Maintains a Redis sorted set of the last N ticks per symbol.
 * Key:   ticks:{SYMBOL}
 * Score: epoch milliseconds of the tick timestamp
 * Value: JSON-serialized Tick
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RedisTickCacheService {

    private static final String KEY_PREFIX = "ticks:";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final AppProperties props;

    public void addTick(Tick tick) {
        try {
            String key   = KEY_PREFIX + tick.getSymbol();
            String value = objectMapper.writeValueAsString(tick);
            double score = (double) tick.getTimestamp().toEpochMilli();

            redisTemplate.opsForZSet().add(key, value, score);

            // Trim: keep only the most recent N entries
            Long size = redisTemplate.opsForZSet().zCard(key);
            if (size != null && size > props.getRedisCacheSize()) {
                redisTemplate.opsForZSet().removeRange(key, 0, size - props.getRedisCacheSize() - 1);
            }
        } catch (JsonProcessingException e) {
            log.error("Failed to cache tick: {}", e.getMessage());
        }
    }

    /**
     * Returns the most recent N ticks for a symbol (latest last).
     */
    public List<Tick> getRecentTicks(String symbol) {
        String key = KEY_PREFIX + symbol;
        Set<String> values = redisTemplate.opsForZSet().reverseRange(key, 0, props.getRedisCacheSize() - 1);
        if (values == null || values.isEmpty()) {
            return Collections.emptyList();
        }

        List<Tick> result = new ArrayList<>();
        for (String v : values) {
            try {
                result.add(objectMapper.readValue(v, Tick.class));
            } catch (JsonProcessingException e) {
                log.warn("Failed to deserialize cached tick: {}", e.getMessage());
            }
        }
        return result;
    }

    /**
     * Returns the single most recent tick for a symbol.
     */
    public Tick getLatestTick(String symbol) {
        String key = KEY_PREFIX + symbol;
        Set<String> values = redisTemplate.opsForZSet().reverseRange(key, 0, 0);
        if (values == null || values.isEmpty()) return null;

        try {
            return objectMapper.readValue(values.iterator().next(), Tick.class);
        } catch (JsonProcessingException e) {
            log.warn("Failed to deserialize latest tick: {}", e.getMessage());
            return null;
        }
    }
}
