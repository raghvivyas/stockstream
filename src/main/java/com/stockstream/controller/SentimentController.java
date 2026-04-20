package com.stockstream.controller;

import com.stockstream.entity.SentimentEntity;
import com.stockstream.model.ApiResponse;
import com.stockstream.model.SentimentDto;
import com.stockstream.repository.SentimentRepository;
import com.stockstream.service.SentimentService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/sentiment")
@RequiredArgsConstructor
public class SentimentController {

    private final SentimentService    sentimentService;
    private final SentimentRepository sentimentRepository;

    /**
     * GET /api/sentiment/{symbol}
     * Returns the latest sentiment result for a symbol.
     */
    @GetMapping("/{symbol}")
    public ResponseEntity<ApiResponse<SentimentDto>> getLatest(@PathVariable String symbol) {
        SentimentDto dto = sentimentService.getLatest(symbol.toUpperCase());
        if (dto == null) {
            return ResponseEntity.ok(ApiResponse.error("No sentiment data yet for " + symbol));
        }
        return ResponseEntity.ok(ApiResponse.ok(dto));
    }

    /**
     * GET /api/sentiment/{symbol}/history?limit=10
     * Returns the last N sentiment analyses for a symbol.
     */
    @GetMapping("/{symbol}/history")
    public ResponseEntity<ApiResponse<List<SentimentDto>>> getHistory(
            @PathVariable String symbol,
            @RequestParam(defaultValue = "10") int limit) {
        List<SentimentEntity> entities = sentimentRepository.findBySymbolOrderByAnalyzedAtDesc(
                symbol.toUpperCase(), PageRequest.of(0, Math.min(limit, 50)));
        List<SentimentDto> dtos = entities.stream().map(this::toDto).collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.ok(dtos));
    }

    /**
     * POST /api/sentiment/{symbol}/trigger
     * Manually triggers a sentiment analysis (useful for demo/testing).
     */
    @PostMapping("/{symbol}/trigger")
    public ResponseEntity<ApiResponse<SentimentDto>> triggerAnalysis(@PathVariable String symbol) {
        SentimentEntity entity = sentimentService.analyzeAndPersist(symbol.toUpperCase());
        return ResponseEntity.ok(ApiResponse.ok("Analysis complete", toDto(entity)));
    }

    private SentimentDto toDto(SentimentEntity e) {
        return SentimentDto.builder()
                .id(e.getId())
                .symbol(e.getSymbol())
                .sentiment(e.getSentiment())
                .score(e.getScore())
                .reasoning(e.getReasoning())
                .analyzedAt(e.getAnalyzedAt())
                .build();
    }
}
