package com.stockstream.scheduler;

import com.stockstream.config.AppProperties;
import com.stockstream.service.SentimentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Runs an AI sentiment analysis for each tracked symbol every 5 minutes.
 * Uses OpenAI if OPENAI_API_KEY is configured; falls back to mock data otherwise.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SentimentAnalysisScheduler {

    private final SentimentService sentimentService;
    private final AppProperties    props;

    // Runs every 5 minutes; initial delay of 60 s to let candles accumulate first
    @Scheduled(initialDelay = 60_000, fixedDelay = 300_000)
    public void runSentimentAnalysis() {
        log.info("Starting scheduled sentiment analysis for {} symbols", props.getSymbols().size());
        for (String symbol : props.getSymbols()) {
            try {
                sentimentService.analyzeAndPersist(symbol);
                log.info("Sentiment persisted for {}", symbol);
            } catch (Exception e) {
                log.error("Sentiment analysis failed for {}: {}", symbol, e.getMessage());
            }
        }
    }
}
