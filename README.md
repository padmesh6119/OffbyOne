# OffbyOne — SQL Arena

A desktop application for Java and SQL practice, with a dedicated SQL competition experience at `/#/sql`.

The SQL editor supports a full-width focus mode, Ctrl/Cmd+Enter to run samples, and Ctrl/Cmd+Shift+Enter to submit.

The SQL arena includes private invite links, best-of-three friendly duels, five-minute rounds, server-owned scores, browser draft recovery, shared rematch lobbies, and post-match explanations. Two wins end a match early. A timeout draws that round; after three rounds the higher score wins, or the match is drawn. Leaving forfeits the match. Friendly arena matches do not change Elo.

All 12 SQL challenges now have a public dataset and four additional datasets (duplicates/ties, sparse data, changed values, and empty tables). Run Sample checks only the public data. Submit checks all datasets and saves the attempt. Hidden results and reference queries are not returned during a match. The arena uses SQLite semantics, not PostgreSQL semantics.

## Local preview without cloud credentials

Requirements: Java 21, Maven 3.9+, Node.js 20+ and npm.

From `backend`, run `mvn spring-boot:run -Dspring-boot.run.profiles=preview`.
The preview runs on port 8087 and uses a separate H2 database at `/tmp/offbyone-preview`. SQL works without Redis, Judge0, or Supabase credentials. Java execution still requires a Judge0 API key.

From `frontend`, run `npm ci`, then `VITE_API_URL=http://127.0.0.1:8087 npm run dev -- --host 127.0.0.1 --port 5177`.
Open `http://127.0.0.1:5177/#/sql`. Use a second browser profile or private window as the opponent.

Guest names must be unique. A new login cannot claim an existing name. The signed guest session remains in its browser until it expires; there is no account recovery flow yet. Do not clear browser storage to resume a guest account.

## Verification

- Backend: `mvn test` from `backend`.
- Frontend: `npm run build` from `frontend`.
- Browser integration: with both preview processes running, install Python Playwright and its Chromium browser, then run `python3 scripts/check_sql_arena.py` from the repository root. Set `ARENA_CHROMIUM` to use an existing Chromium executable. Screenshots go to `/tmp/offbyone-screenshots` by default (`ARENA_SCREENSHOTS` overrides it).
- SQL equivalence checks: `python3 tools/verify_sql_equivalence.py`.

Tests cover SQL typing, NULLs, duplicates, ordering, overflow, execution budgets, sample-only versus hidden judging, match permissions, simultaneous submissions, timeout draws, stale rounds, finalization, and shared rematches. Browser checks use real API calls and two independent sessions.

## Deployment and limits

Keep the existing managed PostgreSQL deployment for production. The new arena reuses the existing schema: `rooms.track` is `sql-arena`, `end_time` is the current round/intermission deadline, and `intermission` is an additional room status. No schema migration is required. The preview profile is local development only.

Set `VITE_API_URL` to your deployed backend when building the frontend. Keep database, JWT, and Java judge credentials on the backend. Existing Render files remain available; this change does not deploy or push automatically.

Arena transitions use a pessimistic database row lock, so competing requests cannot award two winners. The first correct submission admitted by that lock wins; network latency can affect admission order. Deadlines are checked on server requests. Clients poll every two seconds and recover authoritative state after reconnecting; timeouts progress when the match is next accessed. If both players disappear, there is no background tournament scheduler advancing every abandoned round.

SQL uses fresh in-memory databases, read-only query restrictions, instruction/time budgets, result size limits and a four-query concurrency cap. These are resource controls within the API process, not a separate OS sandbox. Validate production PostgreSQL behavior and capacity before a public launch. Rate limiting for the new SQL endpoints is per application instance. Current guest accounts and known problem banks are suitable for friendly competition, not high-stakes ranked assessment.

The original `tools/gen_sql_bank.py` is a historical sample-bank generator. Do not use it to replace the curated bank: it does not generate hidden datasets or match explanations. Author and validate additions in `backend/src/main/resources/sql-problems.json`.
