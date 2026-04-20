package com.stockstream.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SentimentDto {
    private Long id;
    private String symbol;
    private String sentiment;
    private BigDecimal score;
    private String reasoning;
    private Instant analyzedAt;
}
