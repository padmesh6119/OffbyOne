# OffByOne — Full UI & Game Mode Rebuild Spec

You are rebuilding OffByOne from scratch on the frontend, and making targeted backend additions. The current frontend is a skeleton. This spec covers everything. Build it completely.

---

## 1. Theme — "Banana"

| Token | Value |
|---|---|
| Primary | `#FFE135` (banana yellow) |
| Accent | `#FF6B35` (fire orange) |
| Background | `#0D0D0D` |
| Surface | `#161616` |
| Surface-2 | `#1E1E1E` |
| Border | `#2A2A2A` |
| Success | `#00E676` |
| Error | `#FF1744` |
| Text primary | `#FFFFFF` |
| Text muted | `#888888` |

Font: `'Inter'` from Google Fonts. Headings: `font-weight: 800`. Use `letter-spacing: -0.5px` on large text.

Button style: yellow background, black text, bold, pill shape (`border-radius: 999px`), no box-shadow, hover → scale 1.03 + slight brighten.

Cards: `background: #161616`, `border: 1px solid #2A2A2A`, `border-radius: 12px`, `padding: 1.5rem`.

Install: `framer-motion` for animations. Use it for every transition.

---

## 2. Flow

```
/join        → enter name
  ↓
/ (home)     → pick mode: DUEL  |  TOURNAMENT
  ↓                   ↓                ↓
               pick language      pick language
               Java | SQL         Java | SQL
                    ↓                  ↓
               create/join        create/join
               (2 players)        (2–6 players)
                    ↓                  ↓
                  lobby             lobby + question count selector
                    ↓                  ↓
               countdown          countdown
                    ↓                  ↓
               problem reveal     problem reveal
                    ↓                  ↓
               round-by-round     fixed set, leaderboard
               duel               tournament
```

**Never show a list of problems anywhere.** Users never browse or pick problems. Problems are always assigned automatically.

---

## 3. Join Screen (`/join`)

- Full screen, dark background
- Center: big logo "**OffByOne**" in banana yellow, `font-size: 4rem`, `font-weight: 900`
- Tagline: "Code. Compete. Win." in muted gray
- Single input: "Your name" — large, centered, pill shaped, yellow focus border
- Enter key or button submits
- Animate the logo in on load (fade + slide up, framer-motion)
- After join → redirect to `/`

---

## 4. Home Screen (`/`)

Two big mode cards side by side (stack on mobile):

### DUEL card
- Icon: ⚔️ (large)
- Title: "**1v1 Duel**"
- Description: "Same problem. Same time. First to solve wins the round. Play as long as you want."
- Banana yellow border on hover + scale animation

### TOURNAMENT card
- Icon: 🏆
- Title: "**Tournament**"  
- Description: "2–6 players. Fixed problem set. Race to the top of the leaderboard."
- Orange accent border on hover

Click either → modal or new screen asking:
1. **Language**: `Java` | `SQL` (big toggle buttons)
2. Then: **Create Room** or **Join by code** (6-char input)

---

## 5. Duel Mode — Backend + Frontend

### How it works (DIFFERENT from current)
- Exactly 2 players required
- The room auto-assigns **1 problem** at a time (the same problem for both)
- First player to get `accepted` wins that round → gets +1 point
- After a round is won → 5-second "round over" interlude → next problem auto-assigned
- This repeats until either player leaves or disconnects
- Final score = rounds won by each player
- No fixed end time. The duel ends when a player leaves.

### Backend changes needed
Add to `RoomController`:

```
POST /api/rooms/{id}/next-problem
```
- Host-only (auto-called by server when a round ends)
- Pick 1 new random problem not already used in this room
- Broadcast on `/topic/room/{id}/lobby` → `{ event: "round_start", problem: {...} }`

Add field to rooms table: `current_problem_id UUID`, `rounds_played INT DEFAULT 0`

In `JudgeService.awardPoints`: when accepted in a duel room (problem_count=1):
- Increment winner's score (rounds won)
- Broadcast `{ event: "round_won", winner: username, score: {...} }` on `/topic/room/{id}/lobby`
- After 5s delay, auto-call next-problem (use `@Async` + `Thread.sleep(5000)`)

### Duel Screen UI
Layout: full screen split into left (problem) and right (editor + sidebar).

**Top bar**: 
- Left: Player 1 name + score circles (like tennis: 0, 1, 2...)
- Center: banana yellow "VS" 
- Right: Player 2 name + score circles
- No countdown clock (duel is infinite)

**Opponent status indicator** (below top bar):
- "🔴 opponent is thinking..." → "🟡 opponent is typing..." (show when they submit) → "🔴 opponent got wrong answer"
- Update via WebSocket

**Round counter**: "Round 3" in top center

**Problem panel** (left):
- Problem title + difficulty badge
- Statement (formatted, not raw text — parse `\n` into paragraphs)
- For Java: sample input/output in styled code blocks WITH labels ("Sample Input 1", "Sample Output 1")
- For SQL: show the full table schema + 5–6 sample rows in a styled `<table>` BEFORE the question

**Editor panel** (right):
- Monaco editor, `vs-dark` theme
- Language selector (Java/Python/C++) — pill tabs not dropdown
- Submit button (banana yellow, full width at bottom)

**Round start animation**: When a new round starts, problem slides in from right with framer-motion, "ROUND X" flashes in the center.

**Correct answer**: 
- Green flash over the screen
- Confetti explosion (use `canvas-confetti` package)
- "🎉 You solved it!" banner
- Score circles animate

**Wrong answer**: 
- Red border flash + subtle screen shake (CSS `@keyframes shake`)
- Show: "✗ Wrong Answer — Test 3/8 failed" and for sample cases: show "Expected: 5 | Got: 4"

**Round over interlude** (5s between rounds):
- Overlay appears: "[username] won Round X!" 
- Current score big and centered
- Countdown: 5…4…3…2…1 → next problem

---

## 6. Tournament Mode

### How it works
- 2–6 players
- Host picks question count: 2 | 3 | 4 | 5 | 6
- Fixed end time (30 min)
- All get the same N problems simultaneously
- Scoring: base points + first-blood bonus (+50) + speed decay (score shrinks over time)
- WA penalty: -5 per wrong attempt (not on already-solved)
- Tiebreak: solved_count DESC → last_solve_at ASC

### Lobby screen
- Share code: big `XXXXXX` in banana yellow, click to copy
- Player list: animated — each new join slides in
- Host controls: question count toggle (2/3/4/5/6), Start button (disabled until 2+ players)
- Non-host: "Waiting for host..."

### Tournament Screen UI
Layout: same duel-layout (problem left, editor right) but with the leaderboard sidebar.

**Top bar**: Room name | Timer (countdown, turns red under 5 min)

**Problem tabs**: numbered 1–N, green checkmark when solved, banana highlight on active

**Problem panel**: same as duel (with SQL table for SQL problems)

**Leaderboard sidebar**:
- Live updating
- Rank badge: 🥇🥈🥉 for top 3
- Your row highlighted in banana yellow
- Animate rank changes (slide up/down on score update)
- Show solved count next to score

**Results screen**:
- Confetti for winner
- Trophy animation
- Full standings table with score breakdown per problem

---

## 7. SQL Mode — Specific Requirements

Every SQL problem MUST show:

```
┌─────────────────────────────────┐
│  📋 employees                   │
├────┬──────────┬────────┬────────┤
│ id │ name     │ dept   │ salary │
├────┼──────────┼────────┼────────┤
│  1 │ Arun     │ Eng    │  72000 │
│  2 │ Priya    │ Eng    │  64000 │
│  3 │ Karthik  │ HR     │  58000 │
│  4 │ Nila     │ HR     │  62000 │
└────┴──────────┴────────┴────────┘
```

Render this as a proper HTML table (not monospace), styled with the banana theme. Multiple tables if the problem uses joins.

**Query editor**: CodeMirror or Monaco in SQL mode

**Result panel** (below editor, after submit):
- If accepted: show the actual result set in a table (not just "ACCEPTED")
- If wrong: show side by side — "Your Result" | "Expected Result" — highlight differing rows in red
- If error: show the SQLite error message in a styled red box

**Backend note**: `SqlJudgeService` already exists. Add `sampleData` field to SQL problem response — return the CREATE + INSERT statements used to seed the judging DB as JSON so the frontend can render the table.

---

## 8. Judge Feedback — Fix Everything

Current state is broken (shows "judging…" then nothing). Here's what it must show:

### While judging
- Spinner animation on Submit button
- Progress: "Running test 1..." → "Running test 2..." etc. (poll or WebSocket)

### Accepted
```
✓  ACCEPTED   102ms
   Passed all 8 test cases
```
Green banner, confetti (duel only), score update animates.

### Wrong Answer
```
✗  WRONG ANSWER   (test 3 of 8)

   Input:      5 3
   Expected:   8
   Your output: 7
```
Only show for sample test cases (is_sample=true). For hidden cases, just show "Test case 3 failed."

### Compile Error
```
⚠  COMPILE ERROR

   Main.java:4: error: ';' expected
       int x = 5
                ^
```
Show full compile output in a monospace code block.

### TLE
```
⏱  TIME LIMIT EXCEEDED
   Your solution exceeded 3000ms on test case 5
```

### Runtime Error
```
💥  RUNTIME ERROR

   Exception in thread "main" java.lang.ArrayIndexOutOfBoundsException
```

### Backend change needed
The Judge0 response already has this info. Update the WebSocket verdict payload to include:
```json
{
  "verdict": "wrong_answer",
  "runtimeMs": 45,
  "message": "Test 3 of 8 failed",
  "sampleFailed": {
    "input": "5 3",
    "expected": "8",
    "got": "7"
  }
}
```
In `JudgeService.judge()`: track which test case failed (index) and whether it's a sample case. Pass `input`, `tc.getExpectedOutput()`, and `result.stdout()` in the ws message when it's a sample failure.

---

## 9. Gaming Features

- **`canvas-confetti`**: fire on correct answer, big burst on win
- **Screen shake**: CSS animation on wrong answer — `@keyframes shake { 0%,100% { transform: translateX(0) } 25% { transform: translateX(-8px) } 75% { transform: translateX(8px) } }`
- **First blood banner**: when you're first to solve in tournament → slide-in toast "🩸 First Blood! +50 bonus"
- **Rank change**: leaderboard rows animate when rank changes (framer-motion layout animation)
- **Opponent typing indicator**: in duel, show "opponent submitted" when WebSocket event fires for their submission
- **Round start flash**: full-screen flash + "ROUND X — FIGHT" for 1.5 seconds before problem appears
- **Win/lose screen**: full screen overlay, winner gets confetti + trophy emoji animated, loser gets... a banana 🍌
- **Sound** (optional, muted by default): correct = ding, wrong = buzz, round start = beep. Add a mute toggle in nav.
- **"Solving..." indicator**: when opponent gets a WA in duel, show a small red ✗ next to their name

---

## 10. Package additions needed

```bash
npm install framer-motion canvas-confetti
```

`framer-motion` — page transitions, leaderboard animations, modal animations  
`canvas-confetti` — correct answer / win celebration

---

## 11. What NOT to change

- Backend auth flow (JWT, Kahoot-style name join) — keep as is
- WebSocket infrastructure — keep as is
- `api.js` and `ws.js` — add to them, don't rewrite
- Supabase schema — only ADD columns, never drop
- The 29 Java problems + 12 SQL problems already in `problems.json` / `sql-problems.json`

---

## 12. Suggested build order

1. Theme tokens + global CSS (banana colors, fonts, button styles)
2. Join screen
3. Home screen (mode picker)
4. Lobby screen (shared between duel + tournament)
5. Duel screen — problem panel + editor + round flow
6. Tournament screen — problem tabs + leaderboard sidebar
7. SQL mode — table viewer + query editor + result comparison
8. Judge feedback overlay (all verdict states)
9. Gaming effects (confetti, shake, animations)
10. Backend additions (round-based duel, richer verdict payload, SQL sample data)

---

## Reference sites for UI inspiration

- **Codeforces** — problem statement layout
- **Kahoot** — lobby feel, big join code
- **Lichess** — competitive feel, opponent status
- **Valorant rank screens** — winner/loser screen energy
- **HackerRank** — SQL problem with table layout

The vibe is: Kahoot energy + LeetCode problem quality + Valorant competitiveness. Banana yellow everywhere.
