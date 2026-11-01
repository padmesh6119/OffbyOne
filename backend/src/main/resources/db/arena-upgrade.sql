DO $$
DECLARE c record;
BEGIN
  PERFORM pg_advisory_xact_lock(61192026);
  CREATE TABLE IF NOT EXISTS offbyone_migrations (version text PRIMARY KEY, applied_at timestamptz DEFAULT now());
  IF NOT EXISTS (SELECT 1 FROM offbyone_migrations WHERE version='20261031_sql_guests') THEN
    -- Widen old checks without excluding any previously supported values.
    FOR c IN SELECT conname, pg_get_expr(conbin, conrelid) AS expression
      FROM pg_constraint WHERE conrelid='public.submissions'::regclass AND contype='c'
      AND conname IN ('submissions_language_check', 'submissions_verdict_check') LOOP
      EXECUTE format('ALTER TABLE public.submissions DROP CONSTRAINT %I', c.conname);
      IF c.conname='submissions_language_check' THEN
        EXECUTE format('ALTER TABLE public.submissions ADD CONSTRAINT %I CHECK ((%s) OR language = ''sql'')', c.conname, c.expression);
      ELSE
        EXECUTE format('ALTER TABLE public.submissions ADD CONSTRAINT %I CHECK ((%s) OR verdict IN (''accepted'',''wrong_answer'',''error'',''time_limit_exceeded'',''tle''))', c.conname, c.expression);
      END IF;
    END LOOP;
    ALTER TABLE public.users ADD COLUMN IF NOT EXISTS skill_level integer NOT NULL DEFAULT 5;
    ALTER TABLE public.users ADD COLUMN IF NOT EXISTS session_expires_at timestamp;
    ALTER TABLE public.room_participants ADD COLUMN IF NOT EXISTS skill_level integer NOT NULL DEFAULT 5;
    UPDATE public.users SET session_expires_at=created_at + interval '24 hours'
      WHERE email LIKE '%@offbyone.local' AND session_expires_at IS NULL;
    INSERT INTO offbyone_migrations(version) VALUES ('20261031_sql_guests');
  END IF;
  IF NOT EXISTS (SELECT 1 FROM offbyone_migrations WHERE version='20261101_arena_room_checks') THEN
    -- Legacy room states must coexist with the SQL arena lifecycle.
    FOR c IN SELECT conname, pg_get_expr(conbin, conrelid) AS expression
      FROM pg_constraint WHERE conrelid='public.rooms'::regclass AND contype='c'
      AND conname IN ('rooms_status_check', 'rooms_track_check') LOOP
      EXECUTE format('ALTER TABLE public.rooms DROP CONSTRAINT %I', c.conname);
      IF c.conname='rooms_status_check' THEN
        EXECUTE format('ALTER TABLE public.rooms ADD CONSTRAINT %I CHECK ((%s) OR status IN (''waiting'',''active'',''intermission'',''finished''))', c.conname, c.expression);
      ELSE
        EXECUTE format('ALTER TABLE public.rooms ADD CONSTRAINT %I CHECK ((%s) OR track = ''sql-arena'')', c.conname, c.expression);
      END IF;
    END LOOP;
    INSERT INTO offbyone_migrations(version) VALUES ('20261101_arena_room_checks');
  END IF;
END $$;
^^^
