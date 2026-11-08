# OffbyOne — SQL Arena

SQL-only guest arena: choose a name and a self-rated skill from 1–10. Guest passes last 24 hours; expired names are released automatically and can be reused by a new guest. Historical records stay attached to the original guest ID, so reusing a name never grants access to someone else's history. There is no password or account login.

Private duels use the rounded average of both players' levels. New guest passes default to level 1. Levels 1–3 select easy questions, 4–7 medium, and 8–10 hard. Each tier has enough questions for three unique challenges at the selected difficulty. Both players face identical questions. Levels can be adjusted in the lobby and lock when a match starts. This is self-rated difficulty matching for invite duels, not an automatic public matchmaking queue or ranked rating system.

Light and dark themes are available on entry and in the workspace, persist in the browser, and include the SQL editor. Java routes and navigation are removed for now; the older backend and records remain for future work.

The production startup applies `backend/src/main/resources/db/arena-upgrade.sql` before Hibernate validates the schema. The transactional, versioned upgrade widens legacy submission language/verdict checks while preserving earlier allowed values, and adds guest expiry and skill fields. An advisory lock serializes concurrent startup migrations. Repeated starts skip applied migrations. Production credentials must own these tables or have ALTER privileges.

The SQL editor supports a full-width focus mode, Ctrl/Cmd+Enter to run samples, and Ctrl/Cmd+Shift+Enter to submit.

The SQL arena includes private invite links, best-of-three friendly duels, five-minute rounds, server-owned scores, browser draft recovery, shared rematch lobbies, and post-match explanations. Two wins end a match early. A timeout draws that round; after three rounds the higher score wins, or the match is drawn. Leaving forfeits the match. Friendly arena matches do not change Elo.

The bank contains 32 SQL challenges: 20 beginner-friendly easy questions, 8 medium, and 4 hard. Easy questions use one table and cover SELECT, WHERE, ORDER BY, DISTINCT, LIMIT, COUNT, SUM, MIN, MAX, AND/OR, BETWEEN, IN, LIKE, IS NULL, and arithmetic; they require no joins, subqueries, or window functions. The original 12 questions are now medium or hard. Each challenge has a public dataset and four additional datasets covering changed values, boundaries or duplicates, sparse data, and empty tables. Practice also supports bookmarks for the current guest pass in this browser, solved/unsolved/bookmarked filters, a completion bar, and a random challenge drawn from the visible filtered results. A new guest pass clears bookmarks for that name. Practice includes a per-question hint, expected public sample output, and a SQL quick reference. An accepted practice submission unlocks an explained reference solution and a Next unsolved challenge button; these learning panels are absent during duels. Run Sample checks only the public data. Submit checks all datasets and saves the attempt. Hidden results and reference queries are not returned during a match. The arena uses SQLite semantics, not PostgreSQL semantics.

## Local preview without cloud credentials

Requirements: Java 21, Maven 3.8.7+, Node.js 20+ and npm. The combined launcher uses Linux `setsid` to stop both services cleanly.

From the repository root, run `bash run-local.sh` to start both services. Keep that terminal open; Ctrl+C stops them. Startup logs are in `.local-run/`.

From `backend`, run `mvn spring-boot:run -Dspring-boot.run.profiles=preview`.
The preview runs on port 8087 and uses a separate H2 database at `/tmp/offbyone-preview`. SQL works without Redis, Judge0, or Supabase credentials. Java execution still requires a Judge0 API key.

From `frontend`, run `npm ci`, then `VITE_API_URL=http://127.0.0.1:8087 npm run dev -- --host 127.0.0.1 --port 5177`.
Open `http://127.0.0.1:5177/#/sql`. Use a second browser profile or private window as the opponent.

Guest names must be unique while their pass is active. A new pass cannot reclaim an active name. Keep browser storage to resume an unexpired pass.

## Verification

- Backend: `mvn test` from `backend`.
- Frontend: `npm run build` from `frontend`.
- Browser integration: with both preview processes running, install Python Playwright and its Chromium browser, then run `python3 scripts/check_sql_arena.py` from the repository root. Set `ARENA_CHROMIUM` to use an existing Chromium executable. Screenshots go to `/tmp/offbyone-screenshots` by default (`ARENA_SCREENSHOTS` overrides it).
- SQL equivalence checks: `python3 tools/verify_sql_equivalence.py`.

Tests cover SQL typing, NULLs, duplicates, ordering, overflow, execution budgets, sample-only versus hidden judging, match permissions, simultaneous submissions, timeout draws, stale rounds, finalization, and shared rematches. Browser checks use real API calls and two independent sessions.

## Deployment and limits

Keep the existing managed PostgreSQL deployment for production. The new arena reuses the existing schema: `rooms.track` is `sql-arena`, `end_time` is the current round/intermission deadline, and `intermission` is an additional room status. The startup migration updates legacy constraints and adds guest/skill columns. The preview profile is local development only.

Use the repository-root `render.yaml` for Render (the obsolete backend-only blueprint has been removed). It defines a Docker backend rooted at `backend` and a static frontend rooted at `frontend`. For existing services, apply these settings in their dashboard as well: backend health check `/health`; frontend build `npm ci && npm run build`, publish directory `dist`, and rewrite `/*` to `/index.html`.

Backend environment:

- `SUPABASE_DB_PASSWORD`: the password for the existing managed PostgreSQL database.
- `JWT_SECRET`: a private random secret of at least 32 bytes. Keep the same value across deployments so existing unexpired sessions remain valid.
- `JUDGE0_API_KEY` and `REDIS_PASSWORD`: needed for the existing Java execution/rate-limit integrations; SQL does not require them.
- If using a different PostgreSQL database, set `SPRING_DATASOURCE_URL` to its JDBC URL (with `sslmode=require`) and `SPRING_DATASOURCE_USERNAME` to its username. `SPRING_DATASOURCE_PASSWORD` can override the password directly. The defaults point to the original Supabase project.
- Leave `SPRING_PROFILES_ACTIVE` unset. Render startup rejects `preview` and `local`, which use development storage. The backend binds to `0.0.0.0` and Render's `PORT` automatically.

Frontend environment: set `VITE_API_URL` to the backend's public HTTPS origin, for example `https://your-backend.onrender.com` (no `/api`). Rebuild the static site after changing it: the URL is compiled into the bundle. Render builds now reject a missing, local, or malformed URL.

For a **new, empty** database, initialize it with `backend/db/init/001_schema.sql` before deployment. Do not rerun this creation script on an existing database. Startup validates entity tables/columns and fails clearly if the schema is incompatible; the versioned startup migration applies the documented schema changes first. `/health` returns 503 if PostgreSQL is unavailable. The Docker build runs backend tests before packaging.

After deploying, verify `/health`, guest login, one SQL submission, and a duel in two browser sessions. Restart the backend and check the same unexpired session and match load again. Local checks do not verify your production credentials, PostgreSQL locking, or Render capacity. This change does not deploy or push automatically.

Render's [free web services](https://render.com/docs/free) sleep after 15 idle minutes and can take about a minute to wake. Their local filesystem is temporary; keep player and match data in managed PostgreSQL. The SQLite databases used to judge individual queries are intentionally temporary.

Arena transitions use a pessimistic database row lock, so competing requests cannot award two winners. The first correct submission admitted by that lock wins; network latency can affect admission order. Deadlines are checked on server requests. Clients poll every two seconds and recover authoritative state after reconnecting; timeouts progress when the match is next accessed. If both players disappear, there is no background tournament scheduler advancing every abandoned round.

SQL uses fresh in-memory databases, read-only query restrictions, instruction/time budgets, result size limits and a four-query concurrency cap. These are resource controls within the API process, not a separate OS sandbox. Validate production PostgreSQL behavior and capacity before a public launch. Rate limiting for the new SQL endpoints is per application instance. Current guest accounts and known problem banks are suitable for friendly competition, not high-stakes ranked assessment.

The original `tools/gen_sql_bank.py` is a historical sample-bank generator. Do not use it to replace the curated bank: it does not generate hidden datasets or match explanations. Author and validate additions in `backend/src/main/resources/sql-problems.json`.


### Practice and progression

- Daily SQL challenge: the same beginner friendly problem for everyone, rotating at midnight UTC.
- Four guided learning paths with completion tracking and a next challenge.
- XP for distinct SQL solves (100 easy, 200 medium, 350 hard), levels every 500 XP, and six achievement badges. Repeating a problem does not award extra XP.
- Standings for active guest passes, with tied XP sharing a rank. Expired passes disappear from standings.
- My Progress includes a searchable query notebook, private submission review, query restore in the editor, and a downloadable JSON progress snapshot.
- Challenge links survive refresh. Guest passes still expire after 24 hours; an exported snapshot is a personal record, not a login or pass restore mechanism.

These features use existing SQL submissions and the existing database; they require no additional service, migration, or paid API.
