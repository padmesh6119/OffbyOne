# OffByOne — Build Spec

Implementation plan. Written to be executed by a coding agent: every phase has concrete file paths, schemas, API shapes, and **acceptance criteria you can actually run**.

> Read `HANDOFF.md` first — especially **§4 Traps**. It documents failures that cost hours (IPv6 pooler, masked 403s, lazy-loading 500s). Do not rediscover them.

**Rules for whoever/whatever implements this:**
1. Compile locally before pushing. Render builds take 2–4 min; a typo costs one.
   ```bash
   cd backend && docker run --rm -v "$PWD":/app -v m2cache:/root/.m2 -w /app \
     maven:3.9-eclipse-temurin-21 mvn -q clean compile
   ```
2. Every phase ends with its acceptance check passing against a live URL. "It compiles" is not done.
3. Never verify with `/health` — it doesn't touch the DB and returns 200 even when everything else is broken. Verify with a DB-backed endpoint.
4. Don't run untrusted code or SQL against the production Supabase instance. See §9.

---

## 1. Verified current state

Checked against production, not assumed.

| Thing | State |
|---|---|
| Name-only auth → JWT | working |
| Problem bank | **29 problems / 142 test cases** live |
| Rooms: create, join by code, participants | working |
| Host-only actions | enforced (non-host → 403) |
| Judge: accepted / wrong_answer | working, ~100ms |
| Leaderboard ranking | working |
| Rate limiting (5 per 10s) | working — 5 through, 4 × 429 |
| Redis (Upstash) | connected |
| WebSocket | server publishes; **no client subscribes** |
| Duel mode | not built |
| Elo / rating | column exists, never written |
| Judge sandboxing | **none** — see §9 |

**Known data wrinkle:** `sum-two` has 2 test cases; the other 28 have 5. It was hand-created before the seeder existed, and the seeder skips existing slugs. Fix: delete the problem row and let the seeder re-create it.

---

## 2. North star

A **duel platform**, not a practice site. Core loop:

> 2+ players join a room by code → 5 challenges → everyone races → live leaderboard → winner.

Two hard product rules:
- **Never single-player.** A duel requires ≥2 participants to start.
- **Challenges are generated, not hand-written.** Long-term the bank is generators + seeds, not fixed questions.

---

## 3. Phase 0 — cleanup (half a day)

Small, unblocks everything else.

| Task | Detail |
|---|---|
| Delete dead frontend files | `frontend/src/pages/Login.jsx`, `Register.jsx` — leftovers from before the name-only pivot, not routed |
| Re-seed `sum-two` | `DELETE FROM test_cases WHERE problem_id=(SELECT id FROM problems WHERE slug='sum-two'); DELETE FROM problems WHERE slug='sum-two';` then restart — seeder re-creates it with 5 cases |
| Clear debug data | `HANDOFF.md` §9 — 17 users are mostly test accounts |
| Apply duel migration | §4.1 below |

---

## 4. Phase 1 — Duel mode (the core product, ~1 week)

### 4.1 Migration

Not yet applied. Run with `-i` (a heredoc without it silently does nothing — this already bit us once):

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
docker run --rm -i -e PGPASSWORD='<db password>' postgres:16-alpine \
  psql "postgresql://postgres.wpnotthptzpggyqxduft@aws-0-ap-southeast-1.pooler.supabase.com:5432/postgres?sslmode=require" < migration.sql
```

### 4.2 Start rules — `POST /api/rooms/{id}/start`

Modify `RoomController.start`:

1. Reject with **409** if `participantRepo.countByRoomId(id) < 2` → `{"error":"A duel needs at least 2 players"}`
2. If no problems assigned, auto-pick `problem_count` random active problems — spread difficulty (e.g. 2 easy / 2 medium / 1 hard)
3. Set `start_time = now()`, `end_time = now() + duration_minutes`
4. Broadcast `{"event":"started"}` on `/topic/room/{id}/lobby`

### 4.3 Scoring — `JudgeService.awardPoints`

Already correctly ignores repeat solves. Add:

| Rule | Value |
|---|---|
| Base | `RoomProblem.points` (default 100) |
| First blood | +50 to the first player to solve that problem in that room |
| Speed decay | `floor(base × (1 − elapsed/duration × 0.5))` — never below 50% |
| Wrong answer | −5, floored at 0 for that problem |
| Track | increment `solved_count`, set `last_solve_at` |

**Ranking:** `score DESC, last_solve_at ASC` (earlier finish wins ties).

### 4.4 Duel state endpoint

`GET /api/rooms/{id}/state` — one call the frontend can poll as a WS fallback:

```json
{ "room": {"id","name","joinCode","status","startTime","endTime","durationMinutes"},
  "problems": [{"problemId","slug","title","difficulty","points","sortOrder","solvedBy":["username"]}],
  "leaderboard": [{"username","score","solvedCount","rank"}],
  "me": {"username","score","solved":["slug"]} }
```

> Needs `@Transactional(readOnly = true)` — it walks lazy associations. See `HANDOFF.md` §4.3.

### 4.5 Ending a duel

Scheduled sweep (`@Scheduled(fixedDelay=15000)`): any `active` room past `end_time`, or where someone solved all problems → `status='finished'`, broadcast final standings on `/topic/room/{id}/finished`.

### 4.6 Frontend

Backend already publishes these. **Nothing subscribes yet — this is the single biggest gap.**

| Topic | Fires |
|---|---|
| `/topic/room/{id}/lobby` | player joined, duel started |
| `/topic/room/{id}/submission` | any player's verdict |
| `/topic/submission/{id}` | your own verdict |
| `/topic/room/{id}/finished` | duel over (new) |

Screens:
1. **Home** — Create Duel / Join with Code. Remove solo entry points.
2. **Lobby** — live participants, share code, host-only Start (disabled under 2 players, with reason shown).
3. **Duel** — problem tabs 1–5, Monaco editor, countdown, **live leaderboard sidebar**, per-problem solved ticks.
4. **Results** — standings, winner, rating delta.

The live leaderboard during play *is* the product. Prioritise it over polish elsewhere.

### 4.7 Acceptance

```
2 users join → start with 1 player → 409
2nd joins → start → 200, 5 problems auto-assigned
player A solves first → +150 (100 base + 50 first blood)
player B solves same → +100 or less (speed decay)
leaderboard: A rank 1
time expires → status finished, standings broadcast
```

---

## 5. Phase 2 — Challenge Generation Engine (~1 week)

Replaces hand-authored problems with **generators + seeds**. Same challenge from the same seed, different numbers every match, so answers can't be memorised or shared.

### 5.1 Model

```sql
CREATE TABLE generators (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  name TEXT UNIQUE NOT NULL,
  category TEXT NOT NULL,          -- 'java' | 'sql' | 'debug'
  pattern TEXT NOT NULL,           -- 'arrays','two-pointers','dp',...
  rating INTEGER NOT NULL DEFAULT 1200,
  version INTEGER NOT NULL DEFAULT 1,
  config JSONB NOT NULL,
  active BOOLEAN NOT NULL DEFAULT false   -- only true after validation passes
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

### 5.2 Generate ahead of time, not at match time

**Do not generate during a duel.** The Render free instance is 0.1 CPU; generating + running a reference solution on the critical path will stall match start.

Instead: a batch job pre-generates ~50 instances per generator into `challenge_instances`. Matches serve a random low-`used_count` row. Same anti-memorisation benefit, no runtime cost, and validation failures surface at authoring time rather than in front of players.

### 5.3 Each generator ships three things

1. **Generator** — `seed → concrete instance` (must be deterministic)
2. **Reference solution** — produces expected output
3. **Validator** — cross-checks before `active=true`

### 5.4 Validation gate (non-negotiable)

A bad generator poisons *every* match it appears in, unlike a bad hand-written question which poisons one. Before `active=true`:

- Brute-force and optimal references must agree on 100+ random instances
- Boundary instances included: `0`, `1`, empty, all-equal, max constraint
- Same seed → byte-identical instance (determinism check)
- Sample in the statement verified against the reference

> Precedent: the 29 committed problems were validated exactly this way — every expected output cross-checked against an independently written implementation, 0 mismatches. Reuse `/tmp/validate.py` as the model.

**Watch integer division.** A spec'd answer of `ceil(n/step)` implemented in Java as `Math.ceil(n/step)` with ints gives `ceil(5/2) = 2`, not `3`. Use `(n + step - 1) / step`. This class of bug is silent and poisons everything downstream.

### 5.5 Honest limitation

20–50 generators give millions of *instances* of 20–50 *shapes*. It defeats memorising **answers**, not memorising **approaches**. That's still a win — don't build the pitch on the big number.

---

## 6. Phase 3 — SQL duels (the differentiator, ~1 week)

The most distinctive feature available. Nobody in this space does SQL duels well.

### 6.1 Answer by execution

Generate a schema + data, pose a task, run the player's SQL, compare **result sets**. Any logically equivalent query passes — subquery, CTE, window function, join.

```
seed → schema + rows → task statement
player SQL ─┐
reference SQL ─┴→ execute both → compare result sets
```

**You still need a reference query per generator.** What you avoid storing is the canonical query *as the grading key* — not the answer itself.

### 6.2 Run it in SQLite, not Postgres

**Never execute player SQL against the Supabase instance** — that database holds your user rows, and `SELECT * FROM users` would exfiltrate all of them. This is a bigger exposure than the code judge, because it runs *inside* the data.

Use an in-memory **SQLite** DB per submission: build schema, insert generated rows, run the query, discard. Zero blast radius, no extra infra, works on the free tier. You lose some Postgres-only syntax; irrelevant for the question shapes that matter.

If you later need window functions or Postgres semantics, graduate to a throwaway schema with a locked-down role, `statement_timeout`, and no cross-schema grants — but start with SQLite.

Also enforce: single statement only (reject `;`-chained), `SELECT`-only, 2s timeout, row cap.

### 6.3 Result comparison is the fiddly part

Naive `==` produces false negatives, and a correct answer marked wrong kills a duel instantly.

| Case | Rule |
|---|---|
| Row order | Compare as a **multiset** unless the task says "ordered" |
| Duplicates | Preserve — multiset, not set |
| Column names | Compare by position, ignore aliases |
| NULLs | `NULL == NULL` for comparison |
| Floats | Tolerance `1e-6` |
| Types | Normalise int/numeric before compare |

### 6.4 Example generator

```
schema: employees(id, name, dept_id, salary), departments(id, name)
seed → 3–5 departments, 15–40 employees, salaries 30k–150k
task: "Find employees earning more than their department's average salary."
reference: SELECT e.name FROM employees e
           JOIN (SELECT dept_id, AVG(salary) a FROM employees GROUP BY dept_id) d
             ON e.dept_id = d.dept_id WHERE e.salary > d.a;
```

Generator must guarantee a non-empty, non-trivial answer — a task whose correct result is 0 rows is both unsatisfying and passes for many wrong queries.

### 6.5 Acceptance

Three logically different correct queries (subquery / CTE / window) all pass. A wrong one fails. `SELECT * FROM users` cannot reach real data. A 10s query is killed.

---

## 7. Phase 4 — progression & retention

- **Elo** after each duel (K=32, `users.rating` already exists)
- **Profile** — rating graph, solved count, duel W/L, pattern coverage
- **Rivals** — head-to-head record vs each opponent; the strongest retention lever at small scale
- **Streaks / daily duel**
- **Catch-up mechanic** — losing player's next problem worth more. Cheap, counters snowballing, and it's a genuine differentiator vs Codeforces/LeetCode.

Deliberately deferred:
- **Adaptive difficulty** — needs ~50+ solves per problem for signal. With 10–15 players that's months away. Hand-set ratings; wire auto-calibration later or it thrashes on 3 data points.
- **Sabotage/twist rounds** — high balance risk; unfair mechanics kill friend-group games fast.
- **Blind submission** — directly conflicts with the live leaderboard, which is the better hook.
- **Solo path mode** — violates the "never single-player" rule. If wanted, do it as **co-op** instead.

---

## 8. "Kaggle level" — what makes it serious rather than a toy

Not features; properties. These are what separate a class project from a platform.

**Reproducibility.** Every duel replayable from `(generator_version, seed)`. Store the seed, not the rendered challenge. Anyone can regenerate the exact match. This is the single most credible thing in the whole design — it's what "procedurally generated from versioned generators and deterministic seeds" actually buys you.

**Versioned generators.** Bump `version` on change; existing instances stay pinned. Old duels remain valid forever.

**A real quality gate in CI.** Generator validation runs on every PR; a generator that fails cross-check can't merge. Quality enforced by pipeline, not vibes.

**Submission replay.** Store every submission; let players re-watch a duel step by step. Cheap — the rows already exist.

**Public problem format.** `problems.json` is already a clean contract. Document it and people can contribute problems by PR.

**Coverage dashboard.** `generators × pattern × rating band`. Prevents the natural drift toward 80% arrays and 0% graphs.

**Honest stats.** Per-problem solve rate, average time, first-blood rate, language breakdown. Turns the platform into something you can reason about.

**Anti-cheat that isn't theatre.** Generated instances already defeat answer-sharing. Add near-duplicate detection across submissions in the same duel only if stakes ever justify it.

**An API.** `GET /api/challenges/{seed}` returning a deterministic challenge makes the engine usable by others — the difference between a site and a platform.

---

## 9. ⚠️ Security — do before anyone untrusted plays

Two separate problems. Both are real.

**1. Code judge is unsandboxed.** `JudgeService.run()` executes submitted code via `ProcessBuilder` in the backend's own container, as the app user, with network access and env vars in scope — including `SUPABASE_DB_PASSWORD` and `JWT_SECRET`. Any player can read them.

Fix, cheapest first:
1. Hosted judge API (Judge0 / Piston) — removes the problem entirely, fastest path
2. Dedicated judge worker on a VM (Oracle Cloud Always Free) pulling from a queue
3. Docker-per-submission `--network=none --memory=256m --pids-limit=64 --read-only` (needs a Docker socket; unavailable on Render free)

Also missing: stdout size cap (an infinite print fills the disk) and fork limits.

**2. SQL execution** — §6.2. Use SQLite; never the prod instance.

**Credential hygiene:** the DB password has already been rotated once after exposure. Keep secrets out of the repo — GitHub push protection has already blocked one commit containing them.

---

## 10. Order of work

1. **Phase 0** cleanup + migration — half a day
2. **Phase 1** duel mode — the product. Nothing else matters if this isn't fun.
3. **Sandbox the judge** — before sharing the link beyond people you trust
4. **Phase 3** SQL duels — the differentiator, more distinctive than more Java problems
5. **Phase 2** generators for the top ~8 patterns
6. **Phase 4** ratings, rivals, streaks

Phase 3 before Phase 2 is deliberate: SQL-by-execution differentiates the product, while more Java generators only deepen something that already works.

---

## 11. Sizing

| Phase | Effort |
|---|---|
| 0 — cleanup | 0.5 day |
| 1 — duel mode | ~1 week (backend 2–3d, frontend 3–4d) |
| 2 — generation engine | ~1 week |
| 3 — SQL duels | ~1 week (sandbox 2–3d is the hard part) |
| 4 — progression | 3–4 days |
| Judge sandboxing | 1 day hosted / 2–3 days self-hosted |

**~4–5 weeks** to all of it. Phases 0+1 alone give a genuinely playable game — ship that first and get real players on it before building the engine.
