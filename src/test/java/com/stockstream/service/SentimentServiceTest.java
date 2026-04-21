package com.stockstream.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stockstream.config.AppProperties;
import com.stockstream.entity.SentimentEntity;
import com.stockstream.model.SentimentDto;
import com.stockstream.repository.CandleRepository;
import com.stockstream.repository.SentimentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.domain.Pageable;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.Collections;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Pure Mockito unit test for SentimentService.
 * No Spring context, no network, no database.
 */
class SentimentServiceTest {

    @Mock private AppProperties       props;
    @Mock private CandleRepository    candleRepository;
    @Mock private SentimentRepository sentimentRepository;
    @Mock private RestTemplate        restTemplate;

    private SentimentService service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        when(props.getOpenaiApiKey()).thenReturn("");
        when(props.getSymbols()).thenReturn(Collections.singletonList("RELIANCE"));
        service = new SentimentService(props, candleRepository, sentimentRepository,
                restTemplate, new ObjectMapper());
    }

    @Test
    void analyzeAndPersist_withNoApiKey_savesMockSentiment() {
        SentimentEntity saved = SentimentEntity.builder()
                .id(1L).symbol("RELIANCE").sentiment("NEUTRAL")
                .reasoning("Mock analysis").analyzedAt(Instant.now()).build();

        when(candleRepository.findTopNBySymbol(any(), any(Pageable.class)))
                .thenReturn(Collections.emptyList());
        when(sentimentRepository.save(any())).thenReturn(saved);

        SentimentEntity result = service.analyzeAndPersist("RELIANCE");

        assertThat(result).isNotNull();
        assertThat(result.getSymbol()).isEqualTo("RELIANCE");
        verify(sentimentRepository, times(1)).save(any());
    }

    @Test
    void getLatest_whenExists_returnsDto() {
        SentimentEntity entity = SentimentEntity.builder()
                .id(1L).symbol("TCS").sentiment("BULLISH")
                .reasoning("Uptrend detected").analyzedAt(Instant.now()).build();

        when(sentimentRepository.findTopBySymbolOrderByAnalyzedAtDesc("TCS"))
                .thenReturn(Optional.of(entity));

        SentimentDto dto = service.getLatest("TCS");

        assertThat(dto).isNotNull();
        assertThat(dto.getSentiment()).isEqualTo("BULLISH");
    }

    @Test
    void getLatest_whenNotExists_returnsNull() {
        when(sentimentRepository.findTopBySymbolOrderByAnalyzedAtDesc(any()))
                .thenReturn(Optional.empty());
        assertThat(service.getLatest("WIPRO")).isNull();
    }
}
