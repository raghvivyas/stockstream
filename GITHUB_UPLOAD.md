# How to Upload StockStream to GitHub

## Step 1 — Create the Repository on GitHub

1. Go to https://github.com/new
2. Fill in:
   - **Repository name:** `stockstream`
   - **Description:** `Real-time NSE market data pipeline with Kafka, Redis, WebSocket & AI sentiment — Java 8 / Spring Boot`
   - **Visibility:** Public
   - **DO NOT** check "Add README" or "Add .gitignore" — we already have these
3. Click **Create repository**
4. Copy the repository URL, e.g. `https://github.com/YOUR_USERNAME/stockstream.git`

---

## Step 2 — Update README Badges

Open `README.md` and replace both occurrences of `YOUR_USERNAME` with your actual GitHub username:

```
[![CI](https://github.com/YOUR_USERNAME/stockstream/actions/...
```

Also update `docs/DESIGN.md` — replace `<Your Name>` with your actual name at the top.

---

## Step 3 — Initialise Git and Push

Run these commands from inside the `stockstream/` folder:

```bash
cd stockstream

# Initialise local repo
git init

# Stage all files
git add .

# First commit
git commit -m "feat: initial StockStream implementation

- Real-time NSE tick pipeline via Kafka (3 partitions, keyed by symbol)
- 1-minute OHLCV candle aggregation with ConcurrentHashMap (lock-free)
- Redis Sorted Set tick cache (last 50 ticks per symbol, O(log N) insert)
- STOMP WebSocket broadcasting to /topic/ticks and /topic/ticks/{SYMBOL}
- OpenAI GPT-3.5 sentiment analysis with structured prompt + mock fallback
- JWT HS512 authentication (stateless, horizontally scalable)
- Flyway schema migrations (V1: candles, sentiment_results, users)
- Docker Compose full local stack + multi-stage Dockerfile
- AWS ECS task definition, CloudWatch Kafka lag alarm, ECR push script
- GitHub Actions CI pipeline"

# Connect to GitHub
git remote add origin https://github.com/YOUR_USERNAME/stockstream.git

# Push
git branch -M main
git push -u origin main
```

---

## Step 4 — Verify GitHub Actions

1. Go to your repo on GitHub
2. Click the **Actions** tab
3. You should see the CI workflow running
4. The build will fail on the Docker job (expected — no running Kafka/Redis in CI)
   but the **build-and-test** job should pass ✅

---

## Step 5 — Pin to Your GitHub Profile

1. Go to your GitHub profile: https://github.com/YOUR_USERNAME
2. Click **Customize your pins**
3. Select `stockstream` and any other projects you want visible
4. Click **Save pins**

---

## Step 6 — Add Topics to the Repository

On your repository page:
1. Click the gear icon ⚙️ next to "About"
2. Add these topics: `java` `spring-boot` `kafka` `redis` `websocket` `openai` `fintech` `nse` `event-driven` `real-time`
3. Click **Save changes**

Topics help recruiters find your repo via GitHub search.

---

## Commit Message Convention

For all future commits, use [Conventional Commits](https://www.conventionalcommits.org/):

```
feat: add RSI indicator calculation
fix: correct OHLCV high/low aggregation edge case
docs: add Kafka consumer lag explanation to DESIGN.md
refactor: extract candle persistence to async method
test: add integration test for TickConsumer
perf: switch Redis List to Sorted Set for O(log N) insert
chore: upgrade Spring Boot to 2.7.18
```

This shows professionalism and is what engineers at Razorpay, Groww, and Atlassian actually do.
