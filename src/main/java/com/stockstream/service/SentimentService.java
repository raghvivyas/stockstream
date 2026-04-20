package com.stockstream.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stockstream.config.AppProperties;
import com.stockstream.entity.CandleEntity;
import com.stockstream.entity.SentimentEntity;
import com.stockstream.model.SentimentDto;
import com.stockstream.repository.CandleRepository;
import com.stockstream.repository.SentimentRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.*;

/**
 * Calls OpenAI Chat Completions API to analyze sentiment for each NSE symbol.
 * Falls back to deterministic mock sentiment when OPENAI_API_KEY is not configured.
 *
 * Prompt engineering: the model is instructed to respond in a strict format so
 * parsing is deterministic — no JSON mode needed.
 */
@Slf4j
@Service
public class SentimentService {

    private static final String SYSTEM_PROMPT =
            "You are a financial market sentiment analyzer for NSE (National Stock Exchange of India). " +
            "Analyze the given 1-minute candle data and return ONLY the following format, nothing else:\n" +
            "SENTIMENT: <BULLISH|BEARISH|NEUTRAL>\n" +
            "SCORE: <decimal between -1.0 and 1.0>\n" +
            "REASONING: <one concise sentence>";

    private final AppProperties props;
    private final CandleRepository candleRepository;
    private final SentimentRepository sentimentRepository;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public SentimentService(AppProperties props,
                            CandleRepository candleRepository,
                            SentimentRepository sentimentRepository,
                            @Qualifier("openAiRestTemplate") RestTemplate restTemplate,
                            ObjectMapper objectMapper) {
        this.props               = props;
        this.candleRepository    = candleRepository;
        this.sentimentRepository = sentimentRepository;
        this.restTemplate        = restTemplate;
        this.objectMapper        = objectMapper;
    }

    public SentimentEntity analyzeAndPersist(String symbol) {
        List<CandleEntity> candles =
                candleRepository.findTopNBySymbol(symbol, PageRequest.of(0, 10));

        SentimentEntity entity;
        if (isOpenAiConfigured() && !candles.isEmpty()) {
            entity = callOpenAi(symbol, candles);
        } else {
            entity = mockSentiment(symbol);
        }

        return sentimentRepository.save(entity);
    }

    public SentimentDto getLatest(String symbol) {
        return sentimentRepository.findTopBySymbolOrderByAnalyzedAtDesc(symbol)
                .map(this::toDto)
                .orElse(null);
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private boolean isOpenAiConfigured() {
        return props.getOpenaiApiKey() != null && !props.getOpenaiApiKey().trim().isEmpty();
    }

    private SentimentEntity callOpenAi(String symbol, List<CandleEntity> candles) {
        try {
            String userMessage = buildUserPrompt(symbol, candles);

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("model", props.getOpenaiModel());
            body.put("max_tokens", 150);
            body.put("temperature", 0.3);

            List<Map<String, String>> messages = new ArrayList<>();
            Map<String, String> sys = new LinkedHashMap<>();
            sys.put("role", "system");
            sys.put("content", SYSTEM_PROMPT);
            messages.add(sys);

            Map<String, String> usr = new LinkedHashMap<>();
            usr.put("role", "user");
            usr.put("content", userMessage);
            messages.add(usr);

            body.put("messages", messages);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(props.getOpenaiApiKey());

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
            ResponseEntity<String> response = restTemplate.postForEntity(
                    props.getOpenaiBaseUrl() + "/chat/completions", request, String.class);

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                return parseOpenAiResponse(symbol, response.getBody());
            }
        } catch (Exception e) {
            log.error("OpenAI call failed for {}: {}. Falling back to mock.", symbol, e.getMessage());
        }
        return mockSentiment(symbol);
    }

    private SentimentEntity parseOpenAiResponse(String symbol, String responseBody) throws Exception {
        JsonNode root    = objectMapper.readTree(responseBody);
        String content   = root.path("choices").path(0).path("message").path("content").asText("");

        String sentiment = "NEUTRAL";
        BigDecimal score = BigDecimal.ZERO;
        String reasoning = "Analysis unavailable";

        for (String line : content.split("\n")) {
            line = line.trim();
            if (line.startsWith("SENTIMENT:")) {
                String s = line.replace("SENTIMENT:", "").trim();
                if (s.equals("BULLISH") || s.equals("BEARISH") || s.equals("NEUTRAL")) {
                    sentiment = s;
                }
            } else if (line.startsWith("SCORE:")) {
                try {
                    score = new BigDecimal(line.replace("SCORE:", "").trim())
                            .setScale(2, RoundingMode.HALF_UP);
                } catch (NumberFormatException ignored) { /* keep 0 */ }
            } else if (line.startsWith("REASONING:")) {
                reasoning = line.replace("REASONING:", "").trim();
            }
        }

        return SentimentEntity.builder()
                .symbol(symbol)
                .sentiment(sentiment)
                .score(score)
                .reasoning(reasoning)
                .analyzedAt(Instant.now())
                .build();
    }

    private String buildUserPrompt(String symbol, List<CandleEntity> candles) {
        StringBuilder sb = new StringBuilder();
        sb.append("Analyze sentiment for NSE stock: ").append(symbol).append("\n\n");
        sb.append("Last ").append(candles.size()).append(" one-minute candles (oldest first):\n");
        sb.append("OpenTime | Open | High | Low | Close | Volume\n");
        for (CandleEntity c : candles) {
            sb.append(c.getOpenTime()).append(" | ")
              .append(c.getOpen()).append(" | ")
              .append(c.getHigh()).append(" | ")
              .append(c.getLow()).append(" | ")
              .append(c.getClose()).append(" | ")
              .append(c.getVolume()).append("\n");
        }
        return sb.toString();
    }

    private SentimentEntity mockSentiment(String symbol) {
        String[] options = {"BULLISH", "BEARISH", "NEUTRAL"};
        Random rand = new Random(symbol.hashCode() + System.currentTimeMillis() / 300_000);
        String sentiment = options[rand.nextInt(3)];
        double rawScore  = (rand.nextDouble() * 2) - 1;
        BigDecimal score = new BigDecimal(rawScore).setScale(2, RoundingMode.HALF_UP);

        return SentimentEntity.builder()
                .symbol(symbol)
                .sentiment(sentiment)
                .score(score)
                .reasoning("Mock analysis — set OPENAI_API_KEY for AI-powered sentiment")
                .analyzedAt(Instant.now())
                .build();
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
