package com.stockstream.controller;

import com.stockstream.model.Tick;
import com.stockstream.service.MockTickGeneratorService;
import com.stockstream.service.RedisTickCacheService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Arrays;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = TickController.class)
@AutoConfigureMockMvc(addFilters = false)
class TickControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean private RedisTickCacheService redisCache;
    @MockBean private MockTickGeneratorService generator;

    @Test
    void getSymbols_returnsOkWithList() throws Exception {
        when(generator.getSymbols()).thenReturn(Arrays.asList("RELIANCE", "TCS", "INFY"));

        mockMvc.perform(get("/api/ticks/symbols"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0]").value("RELIANCE"));
    }

    @Test
    void getLatestTick_whenCacheEmpty_returnsError() throws Exception {
        when(redisCache.getLatestTick("RELIANCE")).thenReturn(null);

        mockMvc.perform(get("/api/ticks/RELIANCE/latest"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void getLatestTick_whenExists_returnsTick() throws Exception {
        Tick tick = Tick.builder()
                .symbol("RELIANCE")
                .price(new BigDecimal("2855.50"))
                .volume(5000L)
                .timestamp(Instant.now())
                .build();

        when(redisCache.getLatestTick("RELIANCE")).thenReturn(tick);

        mockMvc.perform(get("/api/ticks/RELIANCE/latest"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.symbol").value("RELIANCE"));
    }
}
