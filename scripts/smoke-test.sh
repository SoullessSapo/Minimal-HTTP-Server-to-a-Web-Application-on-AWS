#!/usr/bin/env bash
#
# Manual validation, runnable against any running instance of the application.
#
#   ./scripts/smoke-test.sh                                       # local server on port 8080
#   ./scripts/smoke-test.sh http://localhost:8080 enabled         # also checks that /shutdown
#                                                                 # works, which stops that server
#   ./scripts/smoke-test.sh http://PUBLIC-DNS:8080 disabled       # the cloud deployment
#
# Every check prints the observed HTTP status and content type, and the script fails if an
# expectation is not met. The /shutdown check is the interesting one: it must be 200 in
# development and 404 in production.

set -uo pipefail

BASE="${1:-http://localhost:8080}"
EXPECT_SHUTDOWN="${2:-}"
failures=0

check() {
  local description="$1" expected_status="$2" expected_type="$3" path="$4"
  shift 4
  local observed status type
  observed="$(curl -s -o /dev/null -w '%{http_code} %{content_type}' "$@" "${BASE}${path}" || echo "000 none")"
  status="${observed%% *}"
  type="${observed#* }"

  if [[ "$status" == "$expected_status" && "$type" == *"$expected_type"* ]]; then
    printf '  ok    %-26s %-34s %s %s\n' "$description" "$path" "$status" "$type"
  else
    printf '  FAIL  %-26s %-34s %s %s (expected %s %s)\n' \
      "$description" "$path" "$status" "$type" "$expected_status" "$expected_type"
    failures=$((failures + 1))
  fi
}

body_contains() {
  local description="$1" path="$2" expected="$3"
  local body
  body="$(curl -s "${BASE}${path}")"
  if [[ "$body" == *"$expected"* ]]; then
    printf '  ok    %-26s %-34s %s\n' "$description" "$path" "$body"
  else
    printf '  FAIL  %-26s %-34s %s (expected to contain "%s")\n' \
      "$description" "$path" "$body" "$expected"
    failures=$((failures + 1))
  fi
}

echo "Checking $BASE"
echo
echo "Static resources"
check "home page"        200 "text/html"       "/"
check "style sheet"      200 "text/css"        "/styles.css"
check "client script"    200 "text/javascript" "/app.js"
check "image"            200 "image/png"       "/images/logo.png"

echo
echo "Lambda services"
check "greeting"         200 "text/plain"       "/hello?name=Pedro"
check "pi"               200 "text/plain"       "/pi"
check "square"           200 "text/plain"       "/square?value=7"
check "greeting as JSON" 200 "application/json" "/greeting?name=Ana&language=es"
check "configuration"    200 "application/json" "/config"

echo
echo "Query-string values"
body_contains "name is read"     "/hello?name=Pedro" "Pedro"
body_contains "missing name"     "/hello"            "world"
body_contains "two parameters"   "/greeting?name=Ana&language=es" "es"
body_contains "square of 7"      "/square?value=7"   "49"

echo
echo "Errors that must not stop the server"
check "unknown route"     404 "text/plain" "/unknown"
check "missing file"      404 "text/plain" "/does-not-exist.html"
check "invalid number"    400 "text/plain" "/square?value=abc"
check "missing parameter" 400 "text/plain" "/square"
check "still serving"     200 "text/plain" "/pi"

echo
echo "Lifecycle route"
if [[ -z "$EXPECT_SHUTDOWN" ]]; then
  echo "  skipped  pass 'enabled' or 'disabled' as the second argument to check /shutdown."
  echo "           It is not probed by default because calling it on a development server is"
  echo "           exactly what stops that server."
else
  shutdown_status="$(curl -s -o /dev/null -w '%{http_code}' "${BASE}/shutdown")"
  if [[ "$EXPECT_SHUTDOWN" == "enabled" ]]; then
    if [[ "$shutdown_status" == "200" ]]; then
      echo "  ok       /shutdown answered 200 and the server is stopping (development)"
    else
      echo "  FAIL     /shutdown answered $shutdown_status, expected 200"
      failures=$((failures + 1))
    fi
  else
    if [[ "$shutdown_status" == "404" ]]; then
      echo "  ok       /shutdown is not published: $shutdown_status (production)"
    else
      echo "  FAIL     /shutdown answered $shutdown_status, expected 404 in production"
      failures=$((failures + 1))
    fi
  fi
fi

echo
if [[ $failures -eq 0 ]]; then
  echo "All checks passed."
else
  echo "$failures check(s) failed."
fi
exit $((failures > 0 ? 1 : 0))
