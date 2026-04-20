package com.stockstream.controller;

import com.stockstream.model.ApiResponse;
import com.stockstream.model.Tick;
import com.stockstream.service.MockTickGeneratorService;
import com.stockstream.service.RedisTickCacheService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/ticks")
@RequiredArgsConstructor
public class TickController {

    private final RedisTickCacheService  redisCache;
    private final MockTickGeneratorService generator;

    /**
     * GET /api/ticks/{symbol}/recent
     * Returns the last N ticks from Redis cache for a symbol.
     */
    @GetMapping("/{symbol}/recent")
    public ResponseEntity<ApiResponse<List<Tick>>> getRecentTicks(
            @PathVariable String symbol) {
        List<Tick> ticks = redisCache.getRecentTicks(symbol.toUpperCase());
        return ResponseEntity.ok(ApiResponse.ok(ticks));
    }

    /**
     * GET /api/ticks/{symbol}/latest
     * Returns the single most recent tick from Redis.
     */
    @GetMapping("/{symbol}/latest")
    public ResponseEntity<ApiResponse<Tick>> getLatestTick(
            @PathVariable String symbol) {
        Tick tick = redisCache.getLatestTick(symbol.toUpperCase());
        if (tick == null) {
            return ResponseEntity.ok(ApiResponse.error("No data available for " + symbol));
        }
        return ResponseEntity.ok(ApiResponse.ok(tick));
    }

    /**
     * GET /api/ticks/symbols
     * Returns the list of tracked symbols.
     */
    @GetMapping("/symbols")
    public ResponseEntity<ApiResponse<List<String>>> getSymbols() {
        return ResponseEntity.ok(ApiResponse.ok(generator.getSymbols()));
    }
}
