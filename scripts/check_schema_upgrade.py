"""Verify the production migration on a disposable database in a local Postgres container."""
from pathlib import Path
import subprocess
import sys
import uuid

root = Path(__file__).resolve().parents[1]
container = sys.argv[1] if len(sys.argv) > 1 else 'offbyone-constraint-check'
database = 'arena_migration_' + uuid.uuid4().hex

def sql(statement, db=database):
    subprocess.run(['docker', 'exec', '-i', container, 'psql', '-U', 'postgres', '-d', db,
                    '-v', 'ON_ERROR_STOP=1', '-q'], input=statement, text=True, check=True)

sql(f'CREATE DATABASE {database}', 'postgres')
try:
    sql((root / 'backend/db/init/001_schema.sql').read_text())
    sql("""
      ALTER TABLE submissions ADD CONSTRAINT submissions_language_check CHECK (language IN ('java','python'));
      ALTER TABLE submissions ADD CONSTRAINT submissions_verdict_check CHECK (verdict IN ('pending','AC'));
      ALTER TABLE rooms ADD CONSTRAINT rooms_status_check CHECK (status IN ('running','completed'));
      ALTER TABLE rooms ADD CONSTRAINT rooms_track_check CHECK (track IN ('java','sql'));
      INSERT INTO submissions(language,code,verdict) VALUES ('python','legacy','AC');
      INSERT INTO rooms(join_code,name,status) VALUES ('LEGACY','Legacy room','running');
    """)
    migration = (root / 'backend/src/main/resources/db/arena-upgrade.sql').read_text().replace('^^^', '')
    sql(migration)
    sql(migration)  # Repeated startup must preserve constraints and existing data.
    sql("""
      INSERT INTO submissions(language,code,verdict) VALUES ('sql','SELECT 1','accepted');
      INSERT INTO rooms(join_code,name,status,track)
        SELECT status,'SQL room',status,'sql-arena' FROM unnest(ARRAY['waiting','active','intermission','finished']) status;
      DO $$ BEGIN
        IF (SELECT count(*) FROM offbyone_migrations) <> 2 THEN RAISE EXCEPTION 'Wrong migration count'; END IF;
        IF NOT EXISTS (SELECT 1 FROM submissions WHERE language='python') THEN RAISE EXCEPTION 'Lost legacy submission'; END IF;
        IF NOT EXISTS (SELECT 1 FROM rooms WHERE status='running') THEN RAISE EXCEPTION 'Lost legacy room'; END IF;
        BEGIN
          INSERT INTO submissions(language,code) VALUES ('invalid','bad');
          RAISE EXCEPTION 'Invalid language accepted';
        EXCEPTION WHEN check_violation THEN NULL; END;
        BEGIN
          INSERT INTO rooms(join_code,name,status) VALUES ('BAD','Bad','invalid');
          RAISE EXCEPTION 'Invalid status accepted';
        EXCEPTION WHEN check_violation THEN NULL; END;
      END $$;
    """)
    print('PASS: SQL submissions, all arena states, legacy data, invalid values, and repeated migration')
finally:
    sql(f'DROP DATABASE {database}', 'postgres')
