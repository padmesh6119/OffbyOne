# OffByOne — Developer Handoff

Competitive coding platform. Players join with a name (Kahoot-style, no signup), create or join a room by 6-character code, solve problems, and get judged in real time with a live leaderboard.

**Repo:** https://github.com/padmesh6119/OffbyOne

| Service | URL |
|---|---|
| Frontend (Render static) | https://offbyone-1.onrender.com |
| Backend (Render web service) | https://offbyone-icvk.onrender.com |
| Health check | https://offbyone-icvk.onrender.com/health |

Both on Render free tier → **the backend sleeps after 15 min idle; first request takes ~50s.**

---

## 1. Stack

| Layer | Tech |
|---|---|
| Backend | Java 21, Spring Boot 3.2.5, Maven |
| DB | PostgreSQL (Supabase, Singapore `ap-southeast-1`) |
| Cache | Redis (Upstash, Mumbai) — only used for submission rate limiting |
| Realtime | Spring WebSocket + STOMP over SockJS |
| Judge | In-process: writes code to a temp dir, shells out to `javac`/`g++`/`python3` |
| Frontend | React 18 + Vite, Monaco editor, `@stomp/stompjs` |
| Deploy | Render (Docker for backend, static site for frontend) |

---

## 2. Current state

### Verified working (tested end-to-end against production)

| Feature | Evidence |
|---|---|
| Name-only auth → JWT | `POST /api/auth/join` → 200 + token |
| Problem list / detail / samples | 200 |
| Create room → 6-char join code | 200 |
| Join by code | returns same `roomId` as created |
| Participants list | both players returned |
| Assign problems (host only) | 200; non-host correctly gets **403** |
| Start room | 200 |
| **Judge — correct solution** | verdict `accepted`, 102ms |
| **Judge — wrong solution** | verdict `wrong_answer` |
| Leaderboard with ranks | `player2 rank 1 (100pts)`, `hostuser rank 2 (0)` |
| Auth enforcement | no token → 403 |
| CORS from frontend origin | preflight 200 + correct headers |

**The core engine works.** Auth, rooms, judging, and scoring are all functional.

### Not built yet

- **Duel/tournament mode** — the product direction. See §6.
- **Problem bank not loaded.** `backend/src/main/resources/problems.json` has **29 problems / 145 test cases**, all outputs validated against independent implementations (0 mismatches). **No seeder exists to load it.** The live DB has only 1 problem (`sum-two`).
- **Frontend is a skeleton.** No lobby, no live leaderboard, no timer, no duel flow. It does not subscribe to the WebSocket events the backend already emits.
- **Judge is not sandboxed.** See §5 — this is the most important issue.
- **Elo/rating never written.** `users.rating` exists, defaults to 1200, is never updated.
- **WebSocket not verified from a browser.** Server-side STOMP endpoints exist and the backend publishes events; nobody has confirmed a browser client receives them.

---

## 3. Credentials & environment

### Render — backend web service env vars

| Key | Value / where to get it |
|---|---|
| `SUPABASE_DB_PASSWORD` | `<ask Padmesh — Supabase → Settings → Database>` |
| `JWT_SECRET` | any 32+ char random string — must stay stable or all tokens invalidate |
| `SUPABASE_SERVICE_KEY` | `<ask Padmesh — Supabase → Settings → API → secret key>` |
| `REDIS_PASSWORD` | **NOT SET** — get from Upstash → database → Token |

`REDIS_PASSWORD` being unset is currently harmless — the rate limiter fails open by design — but it means **submission rate limiting is effectively off.** Set it.

### Render — frontend static site

| Key | Value |
|---|---|
| `VITE_API_URL` | `https://offbyone-icvk.onrender.com` |

Build: root dir `frontend`, build command `npm install && npm run build`, publish dir `dist`.

### Supabase

- Project URL: `https://wpnotthptzpggyqxduft.supabase.co`
- Region: **Singapore (`ap-southeast-1`)**
- Anon key: `<Supabase → Settings → API → anon key>`

> **Secrets are deliberately not in this file** — this repo is public. Get them from Padmesh, or read them off the Render dashboard (Environment tab) and Supabase/Upstash consoles.

---

## 4. Traps — read this before debugging anything

These cost hours. Do not rediscover them.

### 4.1 Supabase direct host is IPv6-only — Render free tier is IPv4-only

`db.wpnotthptzpggyqxduft.supabase.co` has **no A record.** Render free cannot reach it. Every DB call fails.

**Always use the pooler:**

```
spring.datasource.url=jdbc:postgresql://aws-0-ap-southeast-1.pooler.supabase.com:5432/postgres
spring.datasource.username=postgres.wpnotthptzpggyqxduft
```

Note the username format: `postgres.<project-ref>`, not `postgres`. Port 5432 is session mode (safe for Hibernate). Port 6543 is transaction mode and needs prepared statements disabled.

Verify any host before blaming code:
```bash
dig +short <host> A      # empty = IPv6-only = unreachable from Render free
```

### 4.2 A bare 403 usually means a masked exception

Spring forwards uncaught exceptions to `/error`. If `/error` isn't permitted, that forward is rejected and you get an **empty 403** — hiding the real error completely. This made a database outage look like an auth bug.

`/error` is now permitted in `SecurityConfig`, and `server.error.include-message=always` is set. **Keep both.**

### 4.3 `open-in-view=false` + LAZY associations = `LazyInitializationException`

`spring.jpa.open-in-view=false` is set (correct practice), so the Hibernate session closes before serialization. Every `@ManyToOne` in this codebase is `FetchType.LAZY`.

Any controller method touching an association needs `@Transactional(readOnly = true)`. Already applied to submission and room read endpoints. **If you add a new endpoint that walks an association and get a 500 saying "could not initialize proxy", this is why.**

### 4.4 Spring Security 6 `requestMatchers(String)` can silently not match

`requestMatchers("/api/auth/**")` builds an `MvcRequestMatcher` that failed to match here while `/ws/**` matched. Use the explicit form:

```java
.requestMatchers(AntPathRequestMatcher.antMatcher("/api/auth/**")).permitAll()
```

### 4.5 Lettuce connects lazily

A wrong/missing Redis password does **not** fail at startup — it fails on the first Redis command. An app that boots fine can still 500 on the first submission.

### 4.6 Build before you push

Render builds take 2–4 min. Compile locally first:

```bash
cd backend
docker run --rm -v "$PWD":/app -v m2cache:/root/.m2 -w /app \
  maven:3.9-eclipse-temurin-21 mvn -q clean compile
```

---

## 5. ⚠️ Security: the judge runs untrusted code unsandboxed

`JudgeService.run()` writes submitted code to a temp directory and executes it with `ProcessBuilder` **in the backend's own container, as the app user, with network access.**

Anyone who can submit code can read env vars (your DB password, JWT secret), open outbound connections, and exhaust the box. The only limits are a wall-clock timeout and `-Xmx` for Java.

Fine for you and friends on a private link. **Do not share publicly until this is sandboxed.**

Options, cheapest first:
1. **Docker-per-submission** — `docker run --rm --network=none --memory=256m --cpus=0.5 --pids-limit=64 --read-only` (needs a Docker socket; not available on Render free).
2. **Dedicated judge worker** on a VM (Oracle Cloud Always Free is a genuinely free option), pulling jobs from a queue.
3. **Hosted judge API** — Judge0 or Piston. Fastest path; removes the problem entirely.

Also missing: output size cap (a program printing infinitely will fill the disk), and process/fork limits.

---

## 6. Roadmap — duel/tournament mode

The intended product: **never single-player.** A duel is 5 problems, 2+ players, everyone racing for first place.

### 6.1 Schema

Not applied — the DB is in its original clean state. Run this when you start:

```sql
ALTER TABLE rooms ADD COLUMN IF NOT EXISTS problem_count INTEGER NOT NULL DEFAULT 5;
ALTER TABLE rooms ADD COLUMN IF NOT EXISTS duration_minutes INTEGER NOT NULL DEFAULT 30;
ALTER TABLE room_participants ADD COLUMN IF NOT EXISTS solved_count INTEGER NOT NULL DEFAULT 0;
ALTER TABLE room_participants ADD COLUMN IF NOT EXISTS last_solve_at TIMESTAMP;
ALTER TABLE problems ADD COLUMN IF NOT EXISTS tags TEXT;

CREATE INDEX IF NOT EXISTS idx_sub_room ON submissions(room_id);
CREATE INDEX IF NOT EXISTS idx_sub_user_problem ON submissions(user_id, problem_id);
CREATE INDEX IF NOT EXISTS idx_tc_problem ON test_cases(problem_id);
```

Run it with `-i` on docker, or the Supabase SQL editor:
```bash
docker run --rm -i -e PGPASSWORD='...' postgres:16-alpine psql "<pooler-url>" < migration.sql
```

### 6.2 Problem seeder (do this first — nothing works without problems)

`problems.json` is committed and validated. Write an `ApplicationRunner` that loads it, idempotent by slug:

```java
@Component
class ProblemSeeder implements ApplicationRunner {
  // for each entry: if problemRepo.findBySlug(slug).isEmpty(),
  //   save Problem, then save its testCases
}
```

JSON shape:
```json
[{ "slug":"sum-two", "title":"...", "difficulty":"easy", "statement":"...",
   "timeLimitMs":3000, "memoryLimitMb":256,
   "testCases":[{"input":"2 3","expected_output":"5","is_sample":true}] }]
```
All problems are stdin→stdout, matching how the judge feeds input. 10 easy / 13 medium / 6 hard.

### 6.3 Duel rules to implement

**Start** (`POST /api/rooms/{id}/start`):
- reject if fewer than **2 participants** — enforces the team-game rule
- if no problems assigned, auto-pick `problem_count` random active problems (mix difficulties)
- set `end_time = now() + duration_minutes`, broadcast `started` on `/topic/room/{id}/lobby`

**Scoring** (extend `JudgeService.awardPoints`, which already correctly ignores repeat solves):
- base points per problem (already there)
- **first-blood bonus** — first player to solve a problem gets extra (e.g. +50)
- **speed decay** — points scale down over elapsed duel time
- **wrong-answer penalty** — small deduction to discourage brute-forcing
- update `solved_count` and `last_solve_at` for tiebreaks

**Ranking:** score DESC, then `last_solve_at` ASC (finishing earlier wins ties).

**End:** when time expires or someone solves all problems → set status `finished`, broadcast final standings, then apply Elo to `users.rating`.

### 6.4 Frontend

The backend already publishes these — nothing subscribes yet:

| Topic | Fires when |
|---|---|
| `/topic/room/{id}/lobby` | player joins, duel starts |
| `/topic/room/{id}/submission` | any player gets a verdict |
| `/topic/submission/{id}` | your own verdict |

Screens to build:
1. **Home** — Create Duel / Join with Code. Remove solo entry points.
2. **Lobby** — live participant list, host-only Start (disabled under 2 players), share code.
3. **Duel** — problem tabs (1–5), Monaco editor, countdown timer, **live leaderboard sidebar**, per-problem solved indicators.
4. **Results** — final standings, winner, rating change, link to solutions.

The live leaderboard during the duel is the whole experience. Prioritise it.

### 6.5 Beyond

- Spectator mode for finished players
- 1v1 quick-match queue
- Per-problem fastest-runtime leaderboard
- Custom "Run" against your own input (separate from Submit)
- Show compile errors — `JudgeService` already captures the message, the UI discards it
- Editorials after a duel ends
- Plagiarism detection once stakes exist

---

## 7. Local development

```bash
git clone https://github.com/padmesh6119/OffbyOne && cd OffbyOne

# Backend (needs the env vars from §3)
cd backend && mvn spring-boot:run          # :8080

# Frontend
cd frontend && npm install && npm run dev  # :5173, proxies /api and /ws to :8080
```

`docker-compose.yml` and `backend/db/init/001_schema.sql` exist for a local Postgres; `application-local.properties` points at it. Run with `-Dspring-boot.run.profiles=local` to avoid touching production data.

---

## 8. File map

```
backend/src/main/java/com/offbyone/
  OffByOneApplication.java          excludes UserDetailsServiceAutoConfiguration
  config/
    SecurityConfig.java             AntPathRequestMatcher permits; JwtFilter as @Bean
    JwtFilter.java                  Bearer token → SecurityContext (NOT @Component — see below)
    JwtUtil.java                    HS256 sign/verify
    WebSocketConfig.java            STOMP /ws, broker /topic
    JacksonConfig.java              Hibernate6Module for lazy proxies
    AsyncConfig.java                @EnableAsync — judge runs off the request thread
  controller/
    AuthController.java             POST /api/auth/join  (name only, upsert user)
    ProblemController.java          CRUD + test cases
    RoomController.java             create/join/participants/assign/start/leaderboard
    SubmissionController.java       submit + read (rate limiter fails open)
    HealthController.java           /health — carries a build marker, useful for verifying deploys
  judge/JudgeService.java           ⚠️ unsandboxed execution + scoring
  model/  repository/
  resources/problems.json           29 validated problems

frontend/src/
  lib/api.js    lib/ws.js
  pages/        Join, Problems, ProblemDetail, Room
```

**`JwtFilter` is deliberately not `@Component`** — as a component Spring Boot also registers it in the plain servlet chain, running it twice. It's constructed as a `@Bean` inside `SecurityConfig` instead.

---

## 9. Housekeeping

Test data from debugging is in the DB — users `pontiff`, `testuser`, `corscheck`, `hostuser`, `player2` and a few rooms. Clear it with:

```sql
DELETE FROM submissions; DELETE FROM room_participants;
DELETE FROM room_problems; DELETE FROM rooms;
DELETE FROM users WHERE username IN ('testuser','corscheck','hostuser','player2');
```

---

## 10. Suggested order of work

1. Apply the §6.1 migration
2. Write the problem seeder → 29 problems live *(everything else depends on this)*
3. Duel start rules: min 2 players + auto-assign 5 random problems
4. Frontend lobby + duel screen with live leaderboard
5. Scoring: first-blood, speed decay, tiebreaks
6. **Sandbox the judge** — before sharing the link with anyone you don't know
7. Elo ratings and profiles
