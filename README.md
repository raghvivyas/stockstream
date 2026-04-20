# StockStream 📈

> Real-time market data pipeline with AI-powered sentiment analysis — built with Java 8, Kafka, Redis, WebSocket, and OpenAI.

**Domain context:** Designed using first-hand knowledge of NSE market data infrastructure. Simulates an NSE tick feed processed through an event-driven pipeline with 1-minute OHLCV candle aggregation, Redis caching, live WebSocket streaming, and GPT-3.5 sentiment analysis.

[![CI](https://github.com/raghvivyas/stockstream/actions/workflows/ci.yml/badge.svg)](https://github.com/raghvivyas/stockstream/actions)
[![Java 8](https://img.shields.io/badge/Java-8-orange.svg)](https://adoptium.net/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-2.7.18-brightgreen.svg)](https://spring.io/projects/spring-boot)

---

## Architecture

```
Tick Simulator ──publish──▶ Kafka (market-ticks, 3 partitions)
                                      │
                           ┌──────────▼──────────┐
                           │    TickConsumer       │
                           └──┬──────┬──────┬─────┘
                              │      │      │
                    ┌─────────▼──┐ ┌─▼───┐ ┌▼──────────┐
                    │  Candle    │ │Redis│ │WebSocket  │
                    │ Aggregator │ │Cache│ │Broadcaster│
                    └─────┬──────┘ └─────┘ └───────────┘
                          │ (finalized candles)
                          ▼
                    PostgreSQL ◀── SentimentScheduler (5 min)
                                        │ OpenAI GPT-3.5
                                        ▼
                                  sentiment_results
```

## Quick Start (3 commands)

```bash
git clone https://github.com/raghvivyas/stockstream.git
cd stockstream
docker-compose up --build
```

App starts at **http://localhost:8080**. Default users are seeded automatically:

| Username | Password     | Role  |
|----------|--------------|-------|
| admin    | password123  | ADMIN |
| demo     | password123  | USER  |

**With AI sentiment** (optional — works without it):
```bash
OPENAI_API_KEY=sk-your-key docker-compose up --build
```

---

## API Reference

### 1. Authentication

**Login**
```bash
curl -s -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"demo","password":"password123"}' | jq .
```

Response:
```json
{
  "success": true,
  "data": {
    "token": "eyJhbGciOiJIUzUxMiJ9...",
    "type": "Bearer",
    "username": "demo",
    "role": "USER"
  }
}
```

**Register a new user**
```bash
curl -s -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"username":"alice","password":"secret123"}' | jq .
```

---

### 2. Market Data (requires JWT)

Set your token first:
```bash
TOKEN=$(curl -s -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"demo","password":"password123"}' | jq -r '.data.token')
```

**Get all tracked symbols**
```bash
curl -s http://localhost:8080/api/ticks/symbols \
  -H "Authorization: Bearer $TOKEN" | jq .
```

**Get latest tick from Redis cache**
```bash
curl -s http://localhost:8080/api/ticks/RELIANCE/latest \
  -H "Authorization: Bearer $TOKEN" | jq .
```

**Get recent 50 ticks (Redis)**
```bash
curl -s http://localhost:8080/api/ticks/RELIANCE/recent \
  -H "Authorization: Bearer $TOKEN" | jq .
```

**Get latest 50 one-minute candles (PostgreSQL)**
```bash
curl -s "http://localhost:8080/api/candles/TCS?limit=50" \
  -H "Authorization: Bearer $TOKEN" | jq .
```

**Get candles in time range**
```bash
curl -s "http://localhost:8080/api/candles/INFY/range?from=2024-01-01T09:00:00Z&to=2024-01-01T10:00:00Z" \
  -H "Authorization: Bearer $TOKEN" | jq .
```

**Get top movers**
```bash
curl -s http://localhost:8080/api/candles/top-movers \
  -H "Authorization: Bearer $TOKEN" | jq .
```

Expected output:
```json
{
  "success": true,
  "data": [
    {
      "symbol": "BAJFINANCE",
      "currentPrice": 6534.21,
      "previousClose": 6520.00,
      "changePercent": 0.22,
      "direction": "UP",
      "volume": 452000
    },
    ...
  ]
}
```

---

### 3. Sentiment Analysis

**Get latest sentiment for a symbol**
```bash
curl -s http://localhost:8080/api/sentiment/RELIANCE \
  -H "Authorization: Bearer $TOKEN" | jq .
```

```json
{
  "success": true,
  "data": {
    "symbol": "RELIANCE",
    "sentiment": "BULLISH",
    "score": 0.72,
    "reasoning": "Strong upward momentum with consistent volume increase over last 10 candles.",
    "analyzedAt": "2024-01-15T10:35:00Z"
  }
}
```

**Trigger manual analysis (for demo/testing)**
```bash
curl -s -X POST http://localhost:8080/api/sentiment/TCS/trigger \
  -H "Authorization: Bearer $TOKEN" | jq .
```

**Get sentiment history**
```bash
curl -s "http://localhost:8080/api/sentiment/HDFCBANK/history?limit=5" \
  -H "Authorization: Bearer $TOKEN" | jq .
```

---

### 4. Live WebSocket Feed

Connect using any STOMP client. Browser example:

```javascript
// Include: sockjs-client and stomp.js (CDN links below)
// <script src="https://cdn.jsdelivr.net/npm/sockjs-client@1/dist/sockjs.min.js"></script>
// <script src="https://cdn.jsdelivr.net/npm/stompjs@2.3.3/lib/stomp.min.js"></script>

const socket = new SockJS('http://localhost:8080/ws');
const stompClient = Stomp.over(socket);

stompClient.connect({}, function() {
  // Subscribe to a single symbol
  stompClient.subscribe('/topic/ticks/RELIANCE', function(message) {
    const tick = JSON.parse(message.body);
    console.log(`${tick.symbol}: ₹${tick.price} @ ${tick.timestamp}`);
  });

  // Or subscribe to all symbols
  stompClient.subscribe('/topic/ticks', function(message) {
    const tick = JSON.parse(message.body);
    console.log(tick);
  });
});
```

Each tick message looks like:
```json
{
  "symbol": "RELIANCE",
  "price": 2853.42,
  "open": 2850.00,
  "high": 2860.15,
  "low": 2848.90,
  "volume": 3200,
  "timestamp": "2024-01-15T10:32:45.123Z"
}
```

---

## Running Without Docker

**Prerequisites:** Java 8, Maven 3.6+, PostgreSQL 14+, Redis 7, Kafka 3+

```bash
# 1. Start infrastructure only
docker-compose up postgres redis zookeeper kafka -d

# 2. Copy and configure environment
cp .env.example .env
# Edit .env — set DB credentials and optionally OPENAI_API_KEY

# 3. Run with Maven (reads .env automatically via IDE or export manually)
export $(cat .env | grep -v '#' | xargs)
./mvnw spring-boot:run
```

---

## Running Tests

```bash
# All tests (unit)
./mvnw test

# With coverage report (target/site/jacoco/index.html)
./mvnw verify
```

Tests are unit-only — they mock Kafka, Redis, and PostgreSQL. No infrastructure needed.

---

## Project Structure

```
stockstream/
├── src/
│   ├── main/java/com/stockstream/
│   │   ├── StockStreamApplication.java   # Entry point
│   │   ├── config/                       # Kafka, Redis, WebSocket, Security, RestTemplate
│   │   ├── model/                        # DTOs (Tick, CandleDto, SentimentDto, JWT)
│   │   ├── entity/                       # JPA entities (Candle, Sentiment, User)
│   │   ├── repository/                   # Spring Data JPA repositories
│   │   ├── kafka/                        # TickProducer, TickConsumer
│   │   ├── service/                      # Aggregator, RedisCache, Sentiment, Auth
│   │   ├── controller/                   # REST: Auth, Tick, Candle, Sentiment, Health
│   │   ├── security/                     # JWT provider, filter, UserDetailsService
│   │   ├── websocket/                    # WebSocketBroadcaster
│   │   └── scheduler/                   # TickSimulator, SentimentScheduler, DataInitializer
│   ├── main/resources/
│   │   ├── application.yml               # All configuration
│   │   └── db/migration/V1__create_tables.sql
│   └── test/java/com/stockstream/
│       ├── StockStreamApplicationTests.java
│       ├── service/CandleAggregatorServiceTest.java
│       ├── service/SentimentServiceTest.java
│       └── controller/TickControllerTest.java
├── docs/
│   └── DESIGN.md                         # Full architecture + design decisions
├── infra/
│   ├── ecs-task-definition.json          # AWS ECS Fargate deployment
│   ├── cloudwatch-alarm.json             # Kafka consumer lag alarm
│   └── ecr-push.sh                       # Docker image push to AWS ECR
├── Dockerfile                            # Multi-stage build
├── docker-compose.yml                    # Full local stack
├── .env.example                          # Environment variable template
└── .github/workflows/ci.yml             # GitHub Actions CI
```

---

## Key Design Decisions

| Decision | Choice | Why |
|----------|--------|-----|
| Message broker | Kafka (3 partitions) | Ordered per-symbol delivery; multiple independent consumers; replay |
| In-memory aggregation | `ConcurrentHashMap#compute` | Lock-free; expensive DB save happens outside the lock |
| Tick cache | Redis Sorted Set | O(log N) insert; time-range queries; efficient trim to N entries |
| Price storage | `NUMERIC(15,4)` | Exact decimal arithmetic — floats cause compounding errors in finance |
| Auth | Stateless JWT HS512 | Horizontal scaling without a shared session store |
| Schema versioning | Flyway | Production-safe; `ddl-auto` is dangerous in production |
| AI response parsing | Structured key-value format | More token-efficient than JSON mode; lower API cost |

Full reasoning in [`docs/DESIGN.md`](docs/DESIGN.md).

---

## Deploying to AWS

```bash
# 1. Push image to ECR
./infra/ecr-push.sh YOUR_ACCOUNT_ID ap-south-1

# 2. Register ECS task definition
aws ecs register-task-definition \
  --cli-input-json file://infra/ecs-task-definition.json \
  --region ap-south-1

# 3. Create CloudWatch alarm for Kafka consumer lag
aws cloudwatch put-metric-alarm \
  --cli-input-json file://infra/cloudwatch-alarm.json \
  --region ap-south-1

# 4. Deploy/update ECS service
aws ecs update-service \
  --cluster stockstream \
  --service stockstream-svc \
  --force-new-deployment \
  --region ap-south-1
```

Full setup guide for RDS, ElastiCache, and MSK in `docs/DESIGN.md` Section 8.

---

## Tech Stack

| Layer | Technology |
|-------|-----------|
| Language | Java 8 |
| Framework | Spring Boot 2.7, Spring Security, Spring Data JPA |
| Messaging | Apache Kafka 3.x |
| Cache | Redis 7 (Sorted Set) |
| Database | PostgreSQL 15, Flyway migrations |
| Real-time | STOMP over WebSocket (SockJS fallback) |
| AI | OpenAI GPT-3.5 Turbo (Chat Completions) |
| Auth | JWT (HS512, jjwt 0.9.1) |
| Cloud | AWS ECS Fargate, RDS, ElastiCache, MSK, CloudWatch |
| Build | Maven, Docker (multi-stage), GitHub Actions |

---

## About

Built to demonstrate event-driven architecture, real-time data pipelines, and AI integration using patterns directly applicable to fintech platforms. The domain model (NSE symbols, OHLCV candles, tick data) reflects real-world experience working with NSE market infrastructure.

