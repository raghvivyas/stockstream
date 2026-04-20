package com.stockstream.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "candles",
       indexes = {
           @Index(name = "idx_candles_symbol_time", columnList = "symbol,open_time DESC")
       })
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CandleEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "symbol", nullable = false, length = 20)
    private String symbol;

    @Column(name = "open", nullable = false, precision = 15, scale = 4)
    private BigDecimal open;

    @Column(name = "high", nullable = false, precision = 15, scale = 4)
    private BigDecimal high;

    @Column(name = "low", nullable = false, precision = 15, scale = 4)
    private BigDecimal low;

    @Column(name = "close", nullable = false, precision = 15, scale = 4)
    private BigDecimal close;

    @Column(name = "volume", nullable = false)
    private Long volume;

    @Column(name = "open_time", nullable = false)
    private Instant openTime;

    @Column(name = "close_time", nullable = false)
    private Instant closeTime;

    @Column(name = "created_at")
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
    }
}
