CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE users (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  username TEXT UNIQUE NOT NULL,
  email TEXT UNIQUE NOT NULL,
  password_hash TEXT NOT NULL,
  rating INTEGER NOT NULL DEFAULT 1200,
  created_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE TABLE problems (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  title TEXT NOT NULL,
  slug TEXT UNIQUE NOT NULL,
  statement TEXT NOT NULL,
  difficulty TEXT NOT NULL,
  time_limit_ms INTEGER NOT NULL DEFAULT 2000,
  memory_limit_mb INTEGER NOT NULL DEFAULT 256,
  is_active BOOLEAN NOT NULL DEFAULT true,
  tags TEXT,
  rating INTEGER NOT NULL DEFAULT 1200,
  created_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE TABLE test_cases (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  problem_id UUID NOT NULL REFERENCES problems(id),
  input TEXT NOT NULL,
  expected_output TEXT NOT NULL,
  is_sample BOOLEAN NOT NULL DEFAULT false
);

CREATE TABLE rooms (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  join_code TEXT UNIQUE NOT NULL,
  name TEXT NOT NULL,
  host_id UUID REFERENCES users(id),
  status TEXT NOT NULL DEFAULT 'waiting',
  start_time TIMESTAMP,
  end_time TIMESTAMP,
  problem_count INTEGER NOT NULL DEFAULT 5,
  track VARCHAR(10) NOT NULL DEFAULT 'java',
  duration_minutes INTEGER NOT NULL DEFAULT 30,
  current_problem_id UUID REFERENCES problems(id),
  current_sql_slug VARCHAR(100),
  rounds_played INTEGER NOT NULL DEFAULT 0,
  winner_user_id UUID REFERENCES users(id),
  created_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE TABLE rating_history (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id UUID NOT NULL REFERENCES users(id),
  room_id UUID REFERENCES rooms(id),
  rating INTEGER NOT NULL,
  delta INTEGER NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_rating_history_user ON rating_history(user_id, created_at);

CREATE TABLE room_participants (
  room_id UUID NOT NULL REFERENCES rooms(id),
  user_id UUID NOT NULL REFERENCES users(id),
  score INTEGER NOT NULL DEFAULT 0,
  rank INTEGER,
  joined_at TIMESTAMP NOT NULL DEFAULT now(),
  solved_count INTEGER NOT NULL DEFAULT 0,
  last_solve_at TIMESTAMP,
  PRIMARY KEY (room_id, user_id)
);

CREATE TABLE room_problems (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  room_id UUID NOT NULL REFERENCES rooms(id),
  problem_id UUID REFERENCES problems(id),
  sql_slug VARCHAR(100),
  points INTEGER NOT NULL DEFAULT 100,
  sort_order INTEGER NOT NULL DEFAULT 0
);

CREATE TABLE submissions (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id UUID REFERENCES users(id),
  problem_id UUID REFERENCES problems(id),
  sql_slug VARCHAR(100),
  room_id UUID REFERENCES rooms(id),
  language TEXT NOT NULL,
  code TEXT NOT NULL,
  verdict TEXT NOT NULL DEFAULT 'pending',
  runtime_ms INTEGER,
  memory_kb INTEGER,
  submitted_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_sub_room ON submissions(room_id);
CREATE INDEX IF NOT EXISTS idx_sub_user_problem ON submissions(user_id, problem_id);
CREATE INDEX IF NOT EXISTS idx_tc_problem ON test_cases(problem_id);
