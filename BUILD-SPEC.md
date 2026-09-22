# OffByOne — Build Spec

Implementation plan for a **two-track duel platform**: Java code duels and SQL duels. Written to be executed by a coding agent — concrete file paths, schemas, API shapes, algorithms, and **runnable acceptance criteria**.

> Read `HANDOFF.md` first, especially **§4 Traps** — IPv6 pooler, masked 403s, lazy-loading 500s. All cost hours. Don't rediscover them.

**Rules for whoever implements this:**

1. Compile locally before pushing — Render builds take 2–4 min, a typo costs one:
   ```bash
   cd backend && docker run --rm -v "$PWD":/app -v m2cache:/root/.m2 -w /app \
     maven:3.9-eclipse-temurin-21 mvn -q clean compile
   ```
2. Each phase ends with its acceptance check passing against a live URL. "It compiles" is not done.
3. **Never verify with `/health`** — it doesn't touch the DB and returns 200 while everything else is broken. Verify with a DB-backed endpoint.
4. Never run untrusted code or SQL against the production Supabase instance. See §9.

---

## 1. Verified current state

Checked against production, not assumed.

| Thing | State |
|---|---|
| Name-only auth → JWT | working |
| Java problem bank | **29 problems / 142 test cases** live |
| **SQL challenge bank** | **12 challenges committed**, execution-verified (§6) |
| Rooms: create, join by code, participants | working |
| Host-only actions | enforced (non-host → 403) |
| Judge: accepted / wrong_answer | working, ~100ms |
| Leaderboard ranking | working |
| Rate limiting 5/10s | working — 5 through, 4 × 429 |
| Redis (Upstash) | connected |
| WebSocket | server publishes; **no client subscribes** ← biggest gap |
| Duel mode | not built |
| SQL execution engine | **not built** — spec in §6 |
| Elo / rating | column exists, never written |
| Judge sandboxing | **none** — §9 |

**Data wrinkle:** `sum-two` has 2 test cases; the other 28 have 5. It was hand-created before the seeder existed and the seeder skips existing slugs. Fix in §3.

---

## 2. North star

A **duel platform**. Never single-player.

> 2+ players join by code → 5 challenges, mixed Java and SQL → everyone races → live leaderboard → winner.

Two tracks, equal weight:

| Track | Player writes | Graded by |
|---|---|---|
| **Code** | Java / Python / C++ reading stdin | stdout compared to expected |
| **SQL** | A query against a generated database | **result set** compared to reference output |

A duel mixes both — e.g. rounds 1,3 Java / 2,5 SQL / 4 debugging. SQL is the differentiator; nobody in this space does SQL duels well.

---

## 3. Phase 0 — cleanup (half a day)

| Task | Detail |
|---|---|
| Delete dead frontend files | `frontend/src/pages/Login.jsx`, `Register.jsx` — pre-pivot leftovers, not routed |
| Re-seed `sum-two` | `DELETE FROM test_cases WHERE problem_id=(SELECT id FROM problems WHERE slug='sum-two'); DELETE FROM problems WHERE slug='sum-two';` then restart — seeder recreates with 5 cases |
| Clear debug data | `HANDOFF.md` §9 — most of the 17 users are test accounts |
| Apply migration | §4.1 |

---

## 4. Phase 1 — Duel mode (~1 week)

### 4.1 Migration

**Use `-i`** — a heredoc without it silently does nothing. This already bit us once:

```sql
ALTER TABLE rooms ADD COLUMN IF NOT EXISTS problem_count INTEGER NOT NULL DEFAULT 5;
ALTER TABLE rooms ADD COLUMN IF NOT EXISTS duration_minutes INTEGER NOT NULL DEFAULT 30;
ALTER TABLE room_participants ADD COLUMN IF NOT EXISTS solved_count INTEGER NOT NULL DEFAULT 0;
ALTER TABLE room_participants ADD COLUMN IF NOT EXISTS last_solve_at TIMESTAMP;
ALTER TABLE problems ADD COLUMN IF NOT EXISTS tags TEXT;
ALTER TABLE problems ADD COLUMN IF NOT EXISTS rating INTEGER NOT NULL DEFAULT 1200;

CREATE INDEX IF NOT EXISTS idx_sub_room ON submissions(room_id);
CREATE INDEX IF NOT EXISTS idx_sub_user_problem ON submissions(user_id, problem_id);
CREATE INDEX IF NOT EXISTS idx_tc_problem ON test_cases(problem_id);
```

```bash
docker run --rm -i -e PGPASSWORD='<pw>' postgres:16-alpine \
  psql "postgresql://postgres.wpnotthptzpggyqxduft@aws-0-ap-southeast-1.pooler.supabase.com:5432/postgres?sslmode=require" < migration.sql
```

### 4.2 Start rules — `POST /api/rooms/{id}/start`

In `RoomController.start`:

1. **409** if `participantRepo.countByRoomId(id) < 2` → `{"error":"A duel needs at least 2 players"}`
2. If nothing assigned, auto-pick `problem_count` challenges — mix difficulty **and** track (e.g. 3 code + 2 SQL)
3. `start_time = now()`, `end_time = now() + duration_minutes`
4. Broadcast `{"event":"started"}` on `/topic/room/{id}/lobby`

### 4.3 Scoring — `JudgeService.awardPoints`

Already ignores repeat solves correctly. Add:

| Rule | Value |
|---|---|
| Base | `RoomProblem.points` (default 100) |
| First blood | +50 to first solver of that problem in that room |
| Speed decay | `floor(base × (1 − elapsed/duration × 0.5))`, never below 50% |
| Wrong answer | −5, floored at 0 for that problem |
| Track | increment `solved_count`, set `last_solve_at` |

**Ranking:** `score DESC, last_solve_at ASC` — earlier finish wins ties.

### 4.4 Duel state endpoint

`GET /api/rooms/{id}/state` — one call, also the WS fallback:

```json
{ "room": {"id","name","joinCode","status","startTime","endTime","durationMinutes"},
  "challenges": [{"type":"code|sql","slug","title","difficulty","points","sortOrder","solvedBy":["username"]}],
  "leaderboard": [{"username","score","solvedCount","rank"}],
  "me": {"username","score","solved":["slug"]} }
```

> Needs `@Transactional(readOnly = true)` — walks lazy associations. `HANDOFF.md` §4.3.

### 4.5 Ending a duel

`@Scheduled(fixedDelay=15000)` sweep: any `active` room past `end_time`, or where a player solved everything → `status='finished'`, broadcast on `/topic/room/{id}/finished`.

### 4.6 Frontend — the biggest gap

The backend already publishes all of these. **Nothing subscribes.** This is the entire live-duel feel.

| Topic | Fires |
|---|---|
| `/topic/room/{id}/lobby` | player joined, duel started |
| `/topic/room/{id}/submission` | any player's verdict |
| `/topic/submission/{id}` | your own verdict |
| `/topic/room/{id}/finished` | duel over (new) |

Screens:
1. **Home** — Create Duel / Join with Code. No solo entry points.
2. **Lobby** — live participants, share code, host-only Start (disabled under 2, with reason shown).
3. **Duel** — challenge tabs 1–5, Monaco editor (Java mode *or* SQL mode + schema viewer), countdown, **live leaderboard sidebar**, solved ticks.
4. **Results** — standings, winner, rating delta.

The live leaderboard *is* the product. Prioritise it.

### 4.7 Acceptance

```
start with 1 player            -> 409
2nd joins, start               -> 200, 5 challenges assigned (mixed code+sql)
A solves first                 -> +150 (100 base + 50 first blood)
B solves same                  -> <=100 (speed decay)
leaderboard                    -> A rank 1
time expires                   -> finished + standings broadcast
```

---

## 5. Phase 2 — Code challenge generation

Generators + seeds replace fixed problems: same challenge from the same seed, different numbers every match, so answers can't be memorised or shared.

### 5.1 Model

```sql
CREATE TABLE generators (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  name TEXT UNIQUE NOT NULL,
  category TEXT NOT NULL,          -- 'java' | 'sql' | 'debug'
  pattern TEXT NOT NULL,
  rating INTEGER NOT NULL DEFAULT 1200,
  version INTEGER NOT NULL DEFAULT 1,
  config JSONB NOT NULL,
  active BOOLEAN NOT NULL DEFAULT false   -- true only after validation passes
);

CREATE TABLE challenge_instances (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  generator_id UUID REFERENCES generators(id),
  seed BIGINT NOT NULL,
  statement TEXT NOT NULL,
  payload JSONB NOT NULL,          -- inputs + expected outputs
  used_count INTEGER NOT NULL DEFAULT 0,
  UNIQUE (generator_id, seed)
);
```

### 5.2 Pre-generate — never at match time

The Render free instance is 0.1 CPU. Generating + running a reference solution on the critical path stalls match start.

Batch-generate ~50 instances per generator into `challenge_instances`; matches serve a random low-`used_count` row. Same anti-memorisation benefit, zero runtime cost, and validation failures surface at authoring time instead of in front of players.

### 5.3 Every generator ships three artifacts

1. **Generator** — `seed → instance`, deterministic
2. **Reference solution** — produces expected output
3. **Validator** — cross-checks before `active=true`

### 5.4 Validation gate — non-negotiable

A bad generator poisons *every* match it appears in, unlike a bad hand-written question which poisons one.

Before `active=true`:
- Brute-force and optimal references agree on 100+ random instances
- Boundary instances: `0`, `1`, empty, all-equal, max constraint
- Same seed → byte-identical instance
- Statement's worked example verified against the reference

> **Precedent:** the 29 committed Java problems were validated exactly this way — every expected output cross-checked against an independently written implementation, 0 mismatches across 50 tricky cases.

**Integer-division trap.** A spec'd `ceil(n/step)` implemented in Java as `Math.ceil(n/step)` with ints gives `ceil(5/2) = 2`, not `3`. Use `(n + step - 1) / step`. Silent, and poisons everything downstream.

### 5.5 Honest limitation

20–50 generators give millions of *instances* of 20–50 *shapes*. It defeats memorising **answers**, not **approaches**. Still a win — don't build the pitch on the big number.

---

## 6. SQL duels — the differentiator

**Status: content built and verified. Engine not built.**

`backend/src/main/resources/sql-problems.json` — **12 challenges**, every one executed against real SQLite, every expected result computed from a reference query, not written by hand.

| Difficulty | Count |
|---|---|
| easy | 4 |
| medium | 6 |
| hard | 2 |

Patterns: `group-by`, `having`, `anti-join`, `join+aggregate`, `scalar-subquery`, `aggregation+subquery`, `ranking`, `date+group-by`, `window-function`.

### 6.1 Answer by execution

Generate schema + data, pose a task, run the player's SQL, compare **result sets**. Any logically equivalent query passes.

```
seed → schema + rows → task
player SQL   ─┐
reference SQL ┴→ execute both → compare result sets
```

**You still need a reference query per challenge.** What you avoid is storing a canonical query *as the grading key*.

### 6.2 Proven

`tools/verify_sql_equivalence.py` — **11/11 passing**:

| Query form | Result |
|---|---|
| Derived-table join (reference) | accepted |
| CTE (`WITH`) | accepted |
| Correlated subquery | accepted |
| Window function (`AVG OVER PARTITION BY`) | accepted |
| Same query with `ORDER BY name DESC` | accepted (order-insensitive) |
| `RANK() OVER` vs correlated MAX | accepted |
| `NOT IN` vs `NOT EXISTS` vs `LEFT JOIN ... IS NULL` | all accepted |
| **`>=` instead of `>`** | **rejected** |
| **Overall average instead of per-department** | **rejected** |

Four syntactically unrelated correct queries accepted; near-miss wrong ones rejected. The approach works.

### 6.3 ⚠️ The lesson that matters most — weak data hides wrong answers

The `>=` vs `>` case **initially passed**. On the original dataset no employee's salary exactly equalled their department average, so `>=` and `>` returned identical rows. The query is wrong in general but indistinguishable on that data.

Fix: add an employee whose salary is **exactly** the department average (`Bala`, Engineering, 64000 — which keeps the average at 64000 and sits on it). Then `>=` includes them, `>` doesn't, and the wrong query fails.

> **Rule: every generated dataset must contain rows that sit exactly on the boundary the task tests.** Off-by-one and `>=`/`>` errors are the most common wrong answers, and data without a boundary row silently accepts them. This applies to every generator, not just SQL — it's the single most important quality rule in this document.

Checklist per SQL generator:
- A row exactly on each comparison boundary (`= avg`, `= max`, `= threshold`)
- A group with exactly one member, and an empty group (tests `JOIN` vs `LEFT JOIN`)
- A NULL in any nullable column used by the task
- Duplicate values (tests `DISTINCT` handling)
- Non-empty, non-trivial expected result — 0 rows passes for many wrong queries

### 6.4 Execute in SQLite, not Postgres

**Never run player SQL against the Supabase instance.** That database holds your user rows; `SELECT * FROM users` would exfiltrate all of them. This is a *larger* exposure than the code judge, because it executes inside the data.

Use in-memory SQLite per submission: build schema, insert generated rows, run query, discard. Zero blast radius, no extra infrastructure, works on the free tier. You lose Postgres-only syntax; irrelevant for these question shapes — note the window-function challenges already work in SQLite.

Graduate later to a throwaway Postgres schema with a locked-down role + `statement_timeout` only if you need Postgres semantics.

Maven:
```xml
<dependency>
  <groupId>org.xerial</groupId>
  <artifactId>sqlite-jdbc</artifactId>
  <version>3.46.1.0</version>
</dependency>
```

### 6.5 `SqlJudgeService` — implementation

New file `backend/src/main/java/com/offbyone/judge/SqlJudgeService.java`:

```java
// 1. jdbc:sqlite::memory:  (fresh connection per submission)
// 2. execute challenge.schema[]  then challenge.seedData[]
// 3. guard the player query (see 6.6), then execute with a 2s timeout
// 4. read ResultSet -> List<List<Object>>, cap 1000 rows
// 5. compare against challenge.expected using the rules in 6.7
// 6. close connection in finally -> in-memory DB is destroyed
```

Verdicts: `accepted`, `wrong_answer`, `sql_error` (syntax/unknown column — return the message, it's good feedback), `tle`, `rejected` (guard tripped).

### 6.6 Guards on player SQL

| Guard | Rule |
|---|---|
| Single statement | reject if `;` appears before trailing whitespace |
| Read-only | must start with `SELECT` or `WITH`; reject `ATTACH`, `PRAGMA`, `INSERT`, `UPDATE`, `DELETE`, `DROP`, `ALTER`, `CREATE` |
| Timeout | `Statement.setQueryTimeout(2)` |
| Row cap | stop reading at 1000 |
| Size cap | reject queries over 10 KB |

SQLite in-memory has no filesystem or network reach, so these guard against runaway cost and confusion, not exfiltration — the isolation does that.

### 6.7 Result comparison algorithm

Naive `==` produces false negatives, and a correct answer marked wrong kills a duel instantly.

| Case | Rule |
|---|---|
| Row order | **Multiset** compare unless `challenge.ordered == true` |
| Duplicates | Preserve — multiset, not set |
| Column names | Compare **by position**, ignore aliases |
| Column count | Must match exactly |
| NULL | `NULL` equals `NULL` |
| Floats | Tolerance `1e-6` |
| Numeric types | Normalise `Integer`/`Long`/`BigDecimal` before compare |
| Strings | Exact, case-sensitive |

Reference implementation (proven in `tools/verify_sql_equivalence.py`):
```python
if challenge["ordered"]: return rows == expected
return sorted(expected, key=repr) == sorted(rows, key=repr)
```
In Java: normalise each row to a canonical string, sort both lists, compare.

### 6.8 Challenge JSON format

```json
{ "slug":"sql-above-dept-avg", "title":"Above Department Average",
  "difficulty":"medium", "rating":1400, "pattern":"aggregation+subquery",
  "schema":["CREATE TABLE departments (...)","CREATE TABLE employees (...)"],
  "seedData":["INSERT INTO departments VALUES ...","INSERT INTO employees VALUES ..."],
  "task":"List the names of employees who earn more than the average salary of their own department.",
  "referenceQuery":"SELECT e.name FROM ...",
  "ordered":false,
  "expected":{"columns":["name"],"rows":[["Ravi"],["Kavi"],["Divya"]]} }
```

`ordered:true` only when the task explicitly says "order by …" — otherwise comparison is order-insensitive.

### 6.9 Schema + seeder

```sql
CREATE TABLE sql_challenges (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  slug TEXT UNIQUE NOT NULL,
  title TEXT NOT NULL,
  difficulty TEXT NOT NULL,
  rating INTEGER NOT NULL DEFAULT 1200,
  pattern TEXT,
  schema_sql JSONB NOT NULL,
  seed_sql JSONB NOT NULL,
  task TEXT NOT NULL,
  reference_query TEXT NOT NULL,
  ordered BOOLEAN NOT NULL DEFAULT false,
  expected JSONB NOT NULL,
  active BOOLEAN NOT NULL DEFAULT true
);
```

Mirror `seed/ProblemSeeder.java` as `seed/SqlChallengeSeeder.java`, loading `sql-problems.json`, idempotent by slug.

### 6.10 API

```
GET  /api/sql-challenges              list (id, slug, title, difficulty, rating, pattern)
GET  /api/sql-challenges/{slug}       task + schema DDL + sample rows (NEVER referenceQuery or expected)
POST /api/sql-submissions             { slug, query, roomId? } -> { submissionId, verdict, ... }
```

**`referenceQuery` and `expected` must never reach the client.** Use a DTO — do not serialise the entity. The frontend needs the schema and a preview of the rows so players can see what they're querying.

### 6.11 Frontend

Reuse Monaco with `language="sql"`. The duel screen needs a **schema panel** — table names, columns, and ~5 sample rows per table. Nobody can write a query against an invisible database.

### 6.12 Regenerating / extending the bank

```bash
python3 tools/gen_sql_bank.py            # regenerates + executes every challenge
python3 tools/verify_sql_equivalence.py  # must print 11 correct / 0 incorrect
```

`gen_sql_bank.py` asserts every reference query returns ≥1 row, so a broken challenge fails at generation rather than in a duel. Add challenges by appending an `add(...)` call.

### 6.13 Acceptance

```
3 logically different correct queries (CTE / correlated / window) -> all accepted
">= instead of >"                                                 -> rejected
"SELECT * FROM users"                                             -> cannot reach real data
"DROP TABLE employees"                                            -> rejected by guard
a 10s query                                                       -> killed at 2s
GET /api/sql-challenges/{slug}                                    -> no referenceQuery, no expected
```

---

## 7. Phase 4 — progression & retention

- **Elo** after each duel (K=32; `users.rating` exists)
- **Profile** — rating graph, solved count, W/L, pattern coverage, **code vs SQL split**
- **Rivals** — head-to-head record; strongest retention lever at small scale
- **Streaks / daily duel**
- **Catch-up** — losing player's next challenge worth more. Cheap, counters snowballing, genuinely differentiating.

Deliberately deferred, with reasons:
- **Adaptive difficulty** — needs ~50+ solves per problem for signal. At 10–15 players that's months. Hand-set ratings; auto-calibration will thrash on 3 data points.
- **Sabotage / twist rounds** — high balance risk; unfair mechanics kill friend-group games.
- **Blind submission** — conflicts with the live leaderboard, which is the better hook.
- **Solo path mode** — violates never-single-player. Do it **co-op** if wanted.

---

## 8. "Kaggle level" — what makes it serious

Properties, not features.

**Reproducibility.** Every duel replayable from `(generator_version, seed)`. Store the seed, not the rendered challenge. This is what "procedurally generated from versioned generators and deterministic seeds" actually buys — the most credible claim in the design.

**Versioned generators.** Bump `version` on change; existing instances stay pinned. Old duels stay valid forever.

**Quality gate in CI.** Run `tools/verify_sql_equivalence.py` and the Java validator on every PR. A generator failing cross-check cannot merge. Quality enforced by pipeline, not vibes.

**Submission replay.** Every submission stored; replay a duel step by step. Rows already exist.

**Open challenge format.** `problems.json` and `sql-problems.json` are clean contracts — document them and people can contribute by PR.

**Coverage dashboard.** `generators × pattern × rating band`. Prevents drift to 80% arrays / 0% graphs.

**Honest stats.** Per-challenge solve rate, average time, first-blood rate, language split. Also feeds rating auto-calibration once there's volume.

**A public API.** `GET /api/challenges/{seed}` returning a deterministic challenge is the difference between a site and a platform.

---

## 9. ⚠️ Security — before anyone untrusted plays

**1. Code judge is unsandboxed.** `JudgeService.run()` executes submitted code via `ProcessBuilder` in the backend's own container, as the app user, with network access and env vars in scope — including `SUPABASE_DB_PASSWORD` and `JWT_SECRET`. Any player can read them.

Cheapest first:
1. **Hosted judge API** (Judge0 / Piston) — removes the problem entirely
2. **Judge worker on a VM** (Oracle Cloud Always Free) pulling from a queue
3. **Docker-per-submission** `--network=none --memory=256m --pids-limit=64 --read-only` (needs a Docker socket; unavailable on Render free)

Also missing: stdout size cap (an infinite print fills the disk) and fork limits.

**2. SQL execution** — §6.4. SQLite in-memory; never the prod instance.

**Credential hygiene:** the DB password has already been rotated once after exposure. Keep secrets out of the repo — GitHub push protection has already blocked one commit containing them.

---

## 10. Order of work

1. **Phase 0** cleanup + migration — half a day
2. **Phase 1** duel mode — nothing else matters if this isn't fun
3. **§6 SQL engine** — content is done and verified; only the executor is missing. Highest value-per-hour in the document.
4. **Sandbox the code judge** — before sharing beyond people you trust
5. **Phase 2** generators for the top ~8 patterns
6. **Phase 4** ratings, rivals, streaks

SQL before code-generators is deliberate: the SQL bank is already built and proven, so it's mostly executor work, and it differentiates the product. More Java generators only deepen something that already works.

---

## 11. Sizing

| Phase | Effort |
|---|---|
| 0 — cleanup | 0.5 day |
| 1 — duel mode | ~1 week (backend 2–3d, frontend 3–4d) |
| **6 — SQL engine** | **2–3 days** (content done; executor + comparison + seeder + UI panel) |
| 2 — code generators | ~1 week |
| 4 — progression | 3–4 days |
| Judge sandboxing | 1 day hosted / 2–3 days self-hosted |

**~3–4 weeks** for all of it. Phases 0+1 alone give a playable game — ship that and get real players on it before building the generation engine.

---

## 12. Repo map

```
backend/src/main/resources/
  problems.json        29 Java problems, 142 test cases   (live)
  sql-problems.json    12 SQL challenges, execution-verified (not yet seeded)

backend/src/main/java/com/offbyone/
  seed/ProblemSeeder.java       loads problems.json, idempotent by slug
  seed/SqlChallengeSeeder.java  TO BUILD — mirror the above
  judge/JudgeService.java       ⚠️ unsandboxed code execution + scoring
  judge/SqlJudgeService.java    TO BUILD — §6.5

tools/
  gen_sql_bank.py              regenerates + executes the SQL bank
  verify_sql_equivalence.py    must print "11 correct / 0 incorrect"
```

The Java bank's generator script was lost to a temp directory; `problems.json` is committed and the validation method is documented in §5.4 if it needs rebuilding.
