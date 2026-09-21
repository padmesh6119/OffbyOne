# OffByOne — Build Log

## What it is
Competitive coding platform. Solo practice + multiplayer rooms. Submit code, get judged, see live leaderboards.

## Stack
| Layer | Tech |
|-------|------|
| Backend | Java 21, Spring Boot 3.2 |
| Database | PostgreSQL via Supabase |
| Cache | Redis via Upstash |
| Real-time | Spring WebSocket (STOMP) |
| Judge | In-process sandbox (fork per submission) |
| Frontend | React + Vite, Monaco editor |
| Hosting | Render (backend), Vercel (frontend) |

---

## Done

- [x] DB schema created on Supabase (7 tables: users, problems, test_cases, rooms, room_participants, room_problems, submissions)
- [x] Spring Boot project scaffolded (`com.offbyone`)
- [x] JWT auth (register + login)
- [x] Problem CRUD + test case management
- [x] Judge service — runs Java/Python/C++ submissions in temp dirs, enforces TLE/MLE/CE/RE/WA
- [x] Submission pipeline — async judge + WebSocket verdict push
- [x] Room system — create room, join by 6-char code, live leaderboard via WebSocket
- [x] React frontend — Login, Register, Problem list, Problem detail with Monaco editor, Room page
- [x] Dockerfile for Render deployment
- [x] Pushed to GitHub → `padmesh6119/OffbyOne`
- [x] Render service created, env vars set, deploying

---

## In Progress

- [ ] Render build passing (Lombok removed, plain Java getters/setters — latest push `82b631e`)
- [ ] Frontend competitive UI pass

---

## To Do

### Backend
- [x] Verify Render deploy succeeds — was actually broken: final Docker stage was JRE-only, no `javac`/`g++`/`python3`, every submission returned CE/RE. Fixed (`backend/Dockerfile`).
- [x] Add problem seeding — seeded + ran one problem end-to-end locally against real schema (see below).
- [x] Rate limiting on `/api/submissions` — 5 submissions / 10s per user via Redis (`SubmissionController`).
- [x] Room problem assignment — `room_participants`/`room_problems` entities added, matching live Supabase schema exactly. `POST/GET /api/rooms/{id}/problems`. Submissions outside a room's assigned set are rejected once problems are assigned; empty = open practice room.
- [ ] Verify Render deploy succeeds (re-check after Dockerfile fix ships)

### Frontend
- [ ] Deploy to Vercel — set `VITE_API_URL` to Render backend URL
- [ ] Profile page (submission history, rating)
- [x] Room lobby shows participants in real-time — join now upserts `room_participants` + broadcasts `/topic/room/{id}/lobby`; `GET /api/rooms/{id}/participants`.
- [ ] Submission history table on problem page — backend DTO ready (`GET /api/submissions/my`), needs UI.

### Judge
- [ ] Isolate judge runs in Docker containers (current: temp dir on same process — fine for 15 users, upgrade if needed)
- [x] Return compile errors to frontend (CE message) — sent transiently over `/topic/submission/{id}` as `message`, not persisted (no `message` column on live `submissions` table).

### Fixed along the way (not on the original list)
- [x] `JudgeService` stdout/stderr pipe deadlock on large output (was reading after `waitFor()`, could hang).
- [x] `GET /api/submissions/{id}` and `/my` were crashing (403, actually a Jackson/Hibernate lazy-proxy serialization error) — now return DTOs.
- [x] Room creation was leaking `User.passwordHash` in the API response — added `@JsonIgnore`.
- [x] Leaderboard double-counted repeated accepted submissions to the same problem — now dedupes per user/problem/room.
- [x] Leaked Redis password in `application.properties` — now `${REDIS_PASSWORD}`.

### Polish
- [ ] Keep Render free instance warm (UptimeRobot ping every 10 min)
- [ ] Add a sample problem via API after first successful deploy

---

## Env Vars (Render)
| Key | Value |
|-----|-------|
| `SUPABASE_DB_PASSWORD` | *(set)* |
| `JWT_SECRET` | *(set)* |
| `SUPABASE_SERVICE_KEY` | *(set)* |

## URLs
- GitHub: https://github.com/padmesh6119/OffbyOne
- Backend (Render): https://offbyone-icvk.onrender.com
- Frontend (Vercel): *not deployed yet*
