package com.stockstream.controller;

import com.stockstream.model.ApiResponse;
import com.stockstream.model.Tick;
import com.stockstream.service.MockTickGeneratorService;
import com.stockstream.service.RedisTickCacheService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Pure Mockito unit test for TickController.
 * No Spring context, no Kafka, no Redis — zero infrastructure dependencies.
 */
class TickControllerTest {

    @Mock
    private RedisTickCacheService redisCache;

    @Mock
    private MockTickGeneratorService generator;

    @InjectMocks
    private TickController controller;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void getSymbols_returnsListFromGenerator() {
        when(generator.getSymbols()).thenReturn(Arrays.asList("RELIANCE", "TCS", "INFY"));

        ResponseEntity<ApiResponse<List<String>>> response = controller.getSymbols();

        assertThat(response.getStatusCodeValue()).isEqualTo(200);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().isSuccess()).isTrue();
        assertThat(response.getBody().getData()).containsExactly("RELIANCE", "TCS", "INFY");
    }

    @Test
    void getLatestTick_whenCacheEmpty_returnsErrorResponse() {
        when(redisCache.getLatestTick("RELIANCE")).thenReturn(null);

        ResponseEntity<ApiResponse<Tick>> response = controller.getLatestTick("RELIANCE");

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().isSuccess()).isFalse();
    }

    @Test
    void getLatestTick_whenTickExists_returnsSuccessResponse() {
        Tick tick = Tick.builder()
                .symbol("RELIANCE")
                .price(new BigDecimal("2855.50"))
                .volume(5000L)
                .timestamp(Instant.now())
                .build();
        when(redisCache.getLatestTick("RELIANCE")).thenReturn(tick);

        ResponseEntity<ApiResponse<Tick>> response = controller.getLatestTick("RELIANCE");

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().isSuccess()).isTrue();
        assertThat(response.getBody().getData().getSymbol()).isEqualTo("RELIANCE");
        assertThat(response.getBody().getData().getPrice()).isEqualByComparingTo("2855.50");
    }

    @Test
    void getRecentTicks_returnsListFromCache() {
        Tick t1 = Tick.builder().symbol("TCS").price(new BigDecimal("3540.00"))
                .volume(1000L).timestamp(Instant.now()).build();
        Tick t2 = Tick.builder().symbol("TCS").price(new BigDecimal("3542.00"))
                .volume(1200L).timestamp(Instant.now()).build();
        when(redisCache.getRecentTicks("TCS")).thenReturn(Arrays.asList(t1, t2));

        ResponseEntity<ApiResponse<List<Tick>>> response = controller.getRecentTicks("TCS");

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().isSuccess()).isTrue();
        assertThat(response.getBody().getData()).hasSize(2);
    }

    @Test
    void getRecentTicks_whenEmpty_returnsEmptyList() {
        when(redisCache.getRecentTicks("WIPRO")).thenReturn(Collections.emptyList());

        ResponseEntity<ApiResponse<List<Tick>>> response = controller.getRecentTicks("WIPRO");

        assertThat(response.getBody().getData()).isEmpty();
    }
}
