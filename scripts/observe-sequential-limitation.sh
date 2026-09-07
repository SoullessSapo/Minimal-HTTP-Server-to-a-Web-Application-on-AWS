#!/usr/bin/env bash
#
# Section 6.2. Reproduces, from the command line, what two browser windows show: while a slow
# request is being served, a second request has to wait, even though the client asked for it
# immediately and asynchronously.
#
#   ./scripts/observe-sequential-limitation.sh [base-url] [seconds]
#
# Reference run: the fast request is sent ~0.5 s after the slow one, yet it only finishes when
# the slow one is over.

set -uo pipefail

BASE="${1:-http://localhost:35000}"
SECONDS_SLOW="${2:-6}"
START="$(date +%s.%N)"

elapsed() { echo "$(date +%s.%N) $START" | awk '{ printf "%6.2f", $1 - $2 }'; }

request() {
  local label="$1" path="$2"
  local sent finished
  sent="$(elapsed)"
  local status
  status="$(curl -s -o /dev/null -w '%{http_code}' "${BASE}${path}")"
  finished="$(elapsed)"
  printf '  %-14s sent at %ss   finished at %ss   status %s\n' "$label" "$sent" "$finished" "$status"
}

echo "Observing the sequential limitation of $BASE"
echo "  slow request: /app/slow?seconds=${SECONDS_SLOW}"
echo

request "slow request" "/app/slow?seconds=${SECONDS_SLOW}" &
slow_pid=$!

sleep 0.5
request "fast request" "/app/time" &
fast_pid=$!

wait "$slow_pid" "$fast_pid"

echo
echo "The fast request was sent half a second after the slow one, but the server could only"
echo "start reading it once the slow response had been written: one connection at a time."
