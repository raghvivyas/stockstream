# StockStream — Design Document

> **Author:** \<raghvivyas\>  
> **Stack:** Java 8 · Spring Boot 2.7 · Kafka · Redis · PostgreSQL · WebSocket · OpenAI · AWS  
> **Domain context:** Built on experience working with NSE market data infrastructure and real-time trading systems.

---

## 1. Problem Statement

Stock exchanges like NSE generate hundreds of thousands of tick events per second. Applications that consume this data need to:

1. **Ingest** tick events reliably and at high throughput.
2. **Aggregate** raw ticks into OHLCV candles for charting.
3. **Serve** live prices to browser clients with sub-second latency.
4. **Cache** recent data to avoid hammering the database on every read.
5. **Enrich** price data with AI-generated sentiment signals for trading insights.

StockStream solves all five using a purpose-built event-driven pipeline.

---

## 2. High-Level Architecture

```
┌─────────────────────────────────────────────────────────────────────────┐
│                         StockStream System                              │
│                                                                         │
│  ┌──────────────┐    publish     ┌─────────────────┐                   │
│  │ Tick         │ ──────────────▶│  Kafka Topic    │                   │
│  │ Simulator    │  market-ticks  │  (3 partitions) │                   │
│  │ (Scheduler)  │                └────────┬────────┘                   │
│  └──────────────┘                         │ consume                    │
│                                           ▼                            │
│                                  ┌─────────────────┐                   │
│                                  │  TickConsumer   │                   │
│                                  └────┬───┬───┬────┘                   │
│                                       │   │   │                        │
│               ┌───────────────────────┘   │   └──────────────────────┐ │
│               ▼                           ▼                          ▼ │
│  ┌────────────────────┐   ┌──────────────────────┐  ┌─────────────────┐│
│  │ CandleAggregator   │   │  RedisTickCache      │  │ WebSocket       ││
│  │ 1-min OHLCV        │   │  ZSet, last 50 ticks │  │ Broadcaster     ││
│  │ ConcurrentHashMap  │   │  per symbol          │  │ /topic/ticks    ││
│  └────────┬───────────┘   └──────────────────────┘  └─────────────────┘│
│           │ finalized candle                                            │
│           ▼                                                             │
│  ┌────────────────────┐                                                 │
│  │   PostgreSQL       │   ┌──────────────────────────────────────────┐ │
│  │   candles table    │◀──│  SentimentScheduler (every 5 min)        │ │
│  │   sentiment table  │   │  reads candles → calls OpenAI → persists │ │
│  │   users table      │   └──────────────────────────────────────────┘ │
│  └────────────────────┘                                                 │
│                                                                         │
│  ┌─────────────────────────────────────────────────────────────────┐   │
│  │                    REST API (Spring MVC)                         │   │
│  │  POST /api/auth/login          GET /api/ticks/{symbol}/latest   │   │
│  │  GET  /api/candles/{symbol}    GET /api/sentiment/{symbol}      │   │
│  │  GET  /api/candles/top-movers  POST /api/sentiment/{s}/trigger  │   │
│  └─────────────────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────────────┘
```

---

## 3. Component Design

### 3.1 Tick Simulator (`MockTickGeneratorService` + `TickSimulatorScheduler`)

Simulates an NSE market feed using a **random-walk price model** — each tick moves ±0.3% from the previous price, which closely approximates real intraday volatility distributions.

**Why random walk?** NSE tick data follows a Brownian motion approximation over short intervals. The mean-reverting random walk used here produces realistic OHLCV candles for demo purposes. In production, this scheduler would be replaced with a WebSocket or FIX protocol connection to the actual NSE feed.

The scheduler fires every 500 ms (configurable via `TICK_INTERVAL_MS`) and publishes one tick per tracked symbol. With 8 symbols at 500 ms, that's **16 Kafka messages/second** — realistic for a demo while being easy on local hardware.

---

### 3.2 Kafka: Why Kafka Over a Simple Queue?

| Requirement | Solution |
|-------------|----------|
| Multiple consumers (candle aggregator + Redis cache + WebSocket) need the same tick | Kafka's consumer group model delivers one copy per group; all three components subscribe independently |
| Message ordering per symbol (close prices must aggregate in sequence) | Symbol is used as the **partition key**, so all ticks for `RELIANCE` land on the same partition, in order |
| Replay capability (re-process ticks for backtesting) | Kafka retains messages; a new consumer with `auto.offset.reset=earliest` can replay the full feed |
| Back-pressure handling | Kafka decouples producer from consumer; if the candle aggregator slows down, ticks queue safely |

**Why not RabbitMQ?** RabbitMQ uses a push model — a slow consumer gets overwhelmed. Kafka's pull model lets consumers pace themselves. For a tick feed that spikes at market open, Kafka's behaviour is far more predictable.

---

### 3.3 Candle Aggregation (`CandleAggregatorService`)

**Algorithm:** time-bucketed aggregation with atomic in-memory state.

```
For each incoming Tick:
  1. Compute minuteBucket = tick.timestamp.truncatedToMinutes()
  2. ConcurrentHashMap.compute(symbol, (k, current) -> {
       if current == null         → create new InProgressCandle
       if current.bucket != now   → toFinalize = current; return new InProgressCandle
       else                       → current.update(tick); return current
     })
  3. If toFinalize != null → candleRepository.save(toFinalize.toEntity())
```

**Key design choice: `ConcurrentHashMap#compute` for lock-free atomicity.**

`compute` holds a bin-level lock for only the duration of the lambda — typically 2–5 µs. The expensive operation (database save) happens *outside* `compute`, so it never blocks other symbols. This is a deliberate trade-off: two threads for the same symbol cannot race because `compute` serializes them on that symbol's bucket.

**Alternative considered:** A dedicated aggregation thread per symbol with a `LinkedBlockingQueue`. Rejected because it adds 8 threads and a queue per symbol for no throughput gain at this scale. The `compute` approach scales to hundreds of symbols with zero additional threads.

---

### 3.4 Redis Tick Cache

**Data structure: Sorted Set (ZSet)**

- Key: `ticks:{SYMBOL}` (e.g., `ticks:RELIANCE`)
- Score: Unix epoch milliseconds of the tick timestamp
- Value: JSON-serialized Tick

**Why ZSet instead of a List?**  
A ZSet with timestamp as score gives us:
- **O(log N)** insertion (vs. O(1) for List, but ZSet deduplicates by score)  
- **Range queries by time** — `ZRANGEBYSCORE` can return ticks between two timestamps in one Redis round-trip
- **Automatic trimming** — `ZREMRANGEBYSCORE` removes old entries efficiently

After each insert, the set is trimmed to the last 50 entries using `ZREMRANGEBYRANK`. This caps memory at approximately `50 × ~400 bytes = 20 KB per symbol`, or `160 KB` for all 8 symbols — negligible.

**Cache hit rate:** Since the scheduler publishes ticks every 500 ms, the `/api/ticks/{symbol}/latest` endpoint will almost always be served from Redis without touching PostgreSQL. This is the critical hot path for live dashboards.

---

### 3.5 WebSocket Broadcasting

Uses Spring's **STOMP over SockJS** — a higher-level messaging protocol on top of raw WebSocket that provides:
- Topic-based pub/sub (clients subscribe to `/topic/ticks/RELIANCE`, not to a raw socket)
- Automatic fallback to HTTP long-polling for browsers that block WebSockets

**Broadcast topology:**
```
/topic/ticks          — all symbols (for a full market overview dashboard)
/topic/ticks/{SYMBOL} — single symbol (for individual stock chart pages)
```

A browser client connecting to the single-symbol channel avoids receiving 7 unused symbol streams, reducing bandwidth by ~87.5%.

---

### 3.6 Sentiment Analysis (`SentimentService`)

**Flow:**
1. Scheduler fires every 5 minutes (configurable).
2. Last 10 candles for the symbol are fetched from PostgreSQL.
3. Candle data is formatted into a structured prompt and sent to OpenAI's Chat Completions API.
4. The response is parsed deterministically from a structured format:
   ```
   SENTIMENT: BULLISH
   SCORE: 0.72
   REASONING: Strong upward momentum with increasing volume over last 10 candles.
   ```
5. Result is persisted to `sentiment_results` with a full audit trail.

**Why structured output format instead of JSON mode?**  
JSON mode (available in GPT-4 Turbo) is overkill for a 3-field response. The structured `KEY: VALUE` format is more token-efficient (lower API cost) and equally deterministic with a simple line-by-line parser.

**Graceful degradation:** If `OPENAI_API_KEY` is not set, or if the API call fails, the service falls back to a deterministic mock that uses a seeded random based on the symbol name and the current 5-minute window. This ensures the application is fully functional for demo purposes with zero API cost.

---

### 3.7 Authentication

**JWT (HS512 signed)** over stateless Spring Security.

Token payload:
```json
{
  "sub": "demo",
  "roles": "ROLE_USER",
  "iat": 1700000000,
  "exp": 1700086400
}
```

**Why stateless JWT instead of sessions?**  
StockStream is designed for horizontal scaling on AWS ECS. Session-based auth requires a shared session store (another Redis dependency) or sticky load balancing. With JWT, any instance can validate any token — no shared state needed.

Token signing uses **HS512** (HMAC-SHA-512) rather than HS256. The longer hash provides stronger collision resistance against brute-force key attacks with negligible performance overhead.

---

## 4. Database Schema

```sql
candles              sentiment_results        users
──────────────────   ──────────────────────   ──────────────────
id         BIGSERIAL  id          BIGSERIAL    id        BIGSERIAL
symbol     VARCHAR    symbol      VARCHAR      username  VARCHAR UNIQUE
open       NUMERIC    sentiment   VARCHAR      password  VARCHAR (bcrypt)
high       NUMERIC    score       NUMERIC      role      VARCHAR
low        NUMERIC    reasoning   TEXT         created_at TIMESTAMPTZ
close      NUMERIC    analyzed_at TIMESTAMPTZ
volume     BIGINT     created_at  TIMESTAMPTZ
open_time  TIMESTAMPTZ
close_time TIMESTAMPTZ
created_at TIMESTAMPTZ

Indexes: candles(symbol, open_time DESC)
         sentiment_results(symbol, analyzed_at DESC)
```

**Why NUMERIC for price instead of FLOAT?**  
IEEE 754 floating-point cannot represent many decimal values exactly. `0.1 + 0.2 = 0.30000000000000004` in Java `double`. For financial data, even a fractional paisa error compounds across aggregations. `NUMERIC(15,4)` is exact and matches the precision used by NSE (prices to 4 decimal places).

**Flyway for migrations:** Every schema change is versioned as a SQL file (`V1__`, `V2__`, …). This means:
- Zero `ddl-auto: create-drop` surprises in production
- Schema changes are code-reviewed and tracked in Git
- Rollback is possible by reversing the migration

---

## 5. Trade-offs and Alternatives Considered

| Decision | Chosen | Alternative | Why chosen |
|----------|--------|-------------|------------|
| Message broker | Kafka | RabbitMQ | Ordered per-symbol delivery; replay capability |
| In-memory state | `ConcurrentHashMap` | Dedicated thread per symbol | Lower thread overhead; simpler code |
| Redis structure | Sorted Set | List | Time-range queries; efficient trim |
| Auth | Stateless JWT | Session + Redis | Horizontal scaling without sticky sessions |
| Price storage | `NUMERIC` | `DOUBLE` | Financial precision; exact decimal arithmetic |
| Schema management | Flyway | `ddl-auto` | Production-safe; version-controlled migrations |
| AI response format | Structured key-value | JSON mode | Fewer tokens; lower cost |

---

## 6. Scalability Considerations

**Current bottleneck:** The candle aggregator holds state in a single JVM's `ConcurrentHashMap`. This works for a single instance but breaks with multiple replicas — two instances would each have a partial view of the ticks.

**Path to horizontal scaling:**
1. Assign each symbol to a Kafka partition and pin each partition to a specific consumer instance via Kafka's consumer group protocol. Symbols don't cross instances.
2. Alternatively, move aggregation state to Redis using a Lua script for atomic OHLCV updates — this is the production approach used by trading platforms.

**CloudWatch alarm added in `infra/`:** Monitors Kafka consumer lag on `market-ticks`. If lag exceeds 1,000 messages, it means the consumer is falling behind the producer — a trigger to scale up the application.

---

## 7. Security Model

| Layer | Control |
|-------|---------|
| REST endpoints | JWT Bearer token required (except `/api/auth/**`, `/actuator/health`) |
| Passwords | BCrypt (cost factor 10) |
| JWT signing | HS512 with configurable secret (minimum 256 bits enforced by algorithm) |
| CORS | Configurable; locked down in production via `ALLOWED_ORIGINS` env var |
| Database credentials | Externalized via environment variables; never in code |
| Docker image | Non-root user (`stockstream`); minimal Alpine JRE base |

---

## 8. AWS Deployment Architecture

```
Internet → ALB (port 443, TLS)
              │
              ▼
          ECS Fargate (stockstream-app)
              │
      ┌───────┴──────────────────────┐
      ▼                              ▼
  AWS RDS PostgreSQL             ElastiCache Redis
  (Multi-AZ)                     (cluster mode)
              │
              ▼
        MSK (Managed Kafka)
```

**Infrastructure files** in `infra/` directory contain:
- CloudFormation template for VPC, subnets, security groups
- ECS task definition with resource limits and environment variable mapping
- CloudWatch log group and Kafka consumer-lag alarm
- ECR push script for Docker image deployment

---

## 9. Running Locally

```bash
# 1. Start infrastructure
docker-compose up postgres redis zookeeper kafka -d

# 2. Run the app (Maven)
./mvnw spring-boot:run

# 3. Or run everything together
docker-compose up --build
```

See **README.md** for complete curl examples and WebSocket connection instructions.

---

## 10. What I Would Add With More Time

1. **Kafka Streams DSL** for the candle aggregator — replaces the `ConcurrentHashMap` with a stateful KTable that Kafka itself manages, enabling true horizontal scaling.
2. **Technical indicators** (RSI, MACD, Bollinger Bands) computed as Kafka Streams processors and stored alongside candles.
3. **Rate limiting** on REST endpoints using Redis token bucket (see Project 4: VaultCache).
4. **gRPC endpoint** for high-frequency clients — REST/JSON overhead matters when polling 8 symbols at 500 ms intervals.
5. **Prometheus + Grafana dashboard** — export Kafka consumer lag, Redis hit rate, and candle throughput as Micrometer metrics.
