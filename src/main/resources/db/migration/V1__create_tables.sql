-- ============================================================
-- StockStream Database Schema
-- V1 — Initial tables: candles, sentiment_results, users
-- ============================================================

CREATE TABLE IF NOT EXISTS candles (
    id         BIGSERIAL    PRIMARY KEY,
    symbol     VARCHAR(20)  NOT NULL,
    open       NUMERIC(15, 4) NOT NULL,
    high       NUMERIC(15, 4) NOT NULL,
    low        NUMERIC(15, 4) NOT NULL,
    close      NUMERIC(15, 4) NOT NULL,
    volume     BIGINT       NOT NULL DEFAULT 0,
    open_time  TIMESTAMPTZ  NOT NULL,
    close_time TIMESTAMPTZ  NOT NULL,
    created_at TIMESTAMPTZ  DEFAULT NOW()
);

CREATE INDEX idx_candles_symbol_time
    ON candles (symbol, open_time DESC);

-- ────────────────────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS sentiment_results (
    id          BIGSERIAL    PRIMARY KEY,
    symbol      VARCHAR(20)  NOT NULL,
    sentiment   VARCHAR(10)  NOT NULL CHECK (sentiment IN ('BULLISH','BEARISH','NEUTRAL')),
    score       NUMERIC(5, 2) NOT NULL,
    reasoning   TEXT,
    analyzed_at TIMESTAMPTZ  NOT NULL,
    created_at  TIMESTAMPTZ  DEFAULT NOW()
);

CREATE INDEX idx_sentiment_symbol_time
    ON sentiment_results (symbol, analyzed_at DESC);

-- ────────────────────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS users (
    id         BIGSERIAL    PRIMARY KEY,
    username   VARCHAR(50)  NOT NULL UNIQUE,
    password   VARCHAR(255) NOT NULL,
    role       VARCHAR(20)  NOT NULL DEFAULT 'USER',
    created_at TIMESTAMPTZ  DEFAULT NOW()
);
