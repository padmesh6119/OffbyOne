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

---

## To Do

### Backend
- [ ] Verify Render deploy succeeds
- [ ] Add problem seeding (insert a few problems + test cases via API to test end-to-end)
- [ ] Rate limiting on `/api/submissions` (prevent spam judging)
- [ ] Room problem assignment (link specific problems to a room before starting)

### Frontend
- [ ] Deploy to Vercel — set `VITE_API_URL` to Render backend URL
- [ ] Profile page (submission history, rating)
- [ ] Room lobby shows participants in real-time
- [ ] Submission history table on problem page

### Judge
- [ ] Isolate judge runs in Docker containers (current: temp dir on same process — fine for 15 users, upgrade if needed)
- [ ] Return compile errors to frontend (CE message)

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
