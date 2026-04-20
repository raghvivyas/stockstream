package com.stockstream.controller;

import com.stockstream.entity.CandleEntity;
import com.stockstream.model.ApiResponse;
import com.stockstream.model.CandleDto;
import com.stockstream.model.TopMoverDto;
import com.stockstream.repository.CandleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/candles")
@RequiredArgsConstructor
public class CandleController {

    private final CandleRepository candleRepository;

    /**
     * GET /api/candles/{symbol}?limit=50
     * Returns recent 1-minute candles for a symbol (newest first).
     */
    @GetMapping("/{symbol}")
    public ResponseEntity<ApiResponse<List<CandleDto>>> getCandles(
            @PathVariable String symbol,
            @RequestParam(defaultValue = "50") int limit) {
        List<CandleEntity> candles = candleRepository.findBySymbolOrderByOpenTimeDesc(
                symbol.toUpperCase(), PageRequest.of(0, Math.min(limit, 200)));
        List<CandleDto> dtos = candles.stream().map(this::toDto).collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.ok(dtos));
    }

    /**
     * GET /api/candles/{symbol}/range?from=&to=
     * Returns candles between two ISO-8601 timestamps.
     */
    @GetMapping("/{symbol}/range")
    public ResponseEntity<ApiResponse<List<CandleDto>>> getCandleRange(
            @PathVariable String symbol,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
        List<CandleEntity> candles = candleRepository.findBySymbolAndTimeRange(
                symbol.toUpperCase(), from, to);
        List<CandleDto> dtos = candles.stream().map(this::toDto).collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.ok(dtos));
    }

    /**
     * GET /api/candles/top-movers
     * Computes % change from the latest candle per symbol.
     */
    @GetMapping("/top-movers")
    public ResponseEntity<ApiResponse<List<TopMoverDto>>> getTopMovers() {
        List<CandleEntity> latest = candleRepository.findLatestCandlePerSymbol();
        List<TopMoverDto> movers = new ArrayList<>();

        for (CandleEntity c : latest) {
            // Previous close ≈ open of the latest candle (simplification for demo)
            BigDecimal prevClose = c.getOpen();
            BigDecimal curr      = c.getClose();

            if (prevClose.compareTo(BigDecimal.ZERO) == 0) continue;

            BigDecimal change = curr.subtract(prevClose)
                    .divide(prevClose, 4, RoundingMode.HALF_UP)
                    .multiply(new BigDecimal("100"))
                    .setScale(2, RoundingMode.HALF_UP);

            movers.add(TopMoverDto.builder()
                    .symbol(c.getSymbol())
                    .currentPrice(curr)
                    .previousClose(prevClose)
                    .changePercent(change)
                    .direction(change.compareTo(BigDecimal.ZERO) >= 0 ? "UP" : "DOWN")
                    .volume(c.getVolume())
                    .build());
        }

        movers.sort((a, b) -> b.getChangePercent().abs().compareTo(a.getChangePercent().abs()));
        return ResponseEntity.ok(ApiResponse.ok(movers));
    }

    private CandleDto toDto(CandleEntity e) {
        return CandleDto.builder()
                .id(e.getId())
                .symbol(e.getSymbol())
                .open(e.getOpen())
                .high(e.getHigh())
                .low(e.getLow())
                .close(e.getClose())
                .volume(e.getVolume())
                .openTime(e.getOpenTime())
                .closeTime(e.getCloseTime())
                .build();
    }
}
