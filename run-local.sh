#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"

for tool in java mvn npm setsid; do
  command -v "$tool" >/dev/null || { echo "Missing prerequisite: $tool"; exit 1; }
done
mkdir -p .local-run
if [[ ! -x frontend/node_modules/.bin/vite ]]; then
  (cd frontend && npm ci)
fi

backend_pid=''
frontend_pid=''
cleanup() {
  trap - EXIT INT TERM
  [[ -z "$frontend_pid" ]] || kill -- "-$frontend_pid" 2>/dev/null || true
  [[ -z "$backend_pid" ]] || kill -- "-$backend_pid" 2>/dev/null || true
  wait 2>/dev/null || true
}
trap cleanup EXIT INT TERM

setsid bash -c 'cd backend && exec mvn spring-boot:run -Dspring-boot.run.profiles=preview' > .local-run/backend.log 2>&1 &
backend_pid=$!
setsid bash -c 'cd frontend && export VITE_API_URL=http://127.0.0.1:8087 && exec npm run dev -- --host 127.0.0.1 --port 5177 --strictPort' > .local-run/frontend.log 2>&1 &
frontend_pid=$!

echo 'Starting OffbyOne at http://127.0.0.1:5177/#/sql'
echo 'Startup may take a moment. Logs: .local-run/backend.log and .local-run/frontend.log'
echo 'Keep this terminal open. Press Ctrl+C to stop.'
wait -n "$backend_pid" "$frontend_pid" || {
  tail -n 30 .local-run/backend.log .local-run/frontend.log
  exit 1
}
