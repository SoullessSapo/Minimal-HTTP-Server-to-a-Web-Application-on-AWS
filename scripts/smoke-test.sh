#!/usr/bin/env bash
#
# Manual validation of section 6.1, runnable against any running instance of the application.
#
#   ./scripts/smoke-test.sh                        # local server on port 35000
#   ./scripts/smoke-test.sh http://EC2-PUBLIC-DNS:35000
#
# Every check prints the observed HTTP status and content type, and the script fails if any
# expectation is not met.

set -uo pipefail

BASE="${1:-http://localhost:35000}"
failures=0

check() {
  local description="$1" expected_status="$2" expected_type="$3" path="$4"
  shift 4
  local observed status type
  # Extra curl options may follow, for example --path-as-is so that curl does not resolve
  # ".." itself and the server is the one that has to reject the path.
  observed="$(curl -s -o /dev/null -w '%{http_code} %{content_type}' "$@" "${BASE}${path}" || echo "000 none")"
  status="${observed%% *}"
  type="${observed#* }"

  if [[ "$status" == "$expected_status" && "$type" == *"$expected_type"* ]]; then
    printf '  ok    %-28s %-38s %s %s\n' "$description" "$path" "$status" "$type"
  else
    printf '  FAIL  %-28s %-38s %s %s (expected %s %s)\n' \
      "$description" "$path" "$status" "$type" "$expected_status" "$expected_type"
    failures=$((failures + 1))
  fi
}

echo "Checking $BASE"
echo
echo "Static resources"
check "home page"            200 "text/html"        "/"
check "style sheet"          200 "text/css"         "/styles.css"
check "client script"        200 "text/javascript"  "/app.js"
check "PNG image"            200 "image/png"        "/images/logo.png"
check "JPEG image"           200 "image/jpeg"       "/images/request-flow.jpg"

echo
echo "Hardcoded services"
check "greeting"             200 "application/json" "/app/hello?name=Esteban"
check "square"               200 "application/json" "/app/square?value=7"
check "server time"          200 "application/json" "/app/time"
check "health"               200 "application/json" "/health"

echo
echo "Controlled errors"
check "greeting without name" 400 "application/json" "/app/hello"
check "square with a word"    400 "application/json" "/app/square?value=abc"
check "missing static file"   404 "text/html"        "/does-not-exist.html"
check "unsupported method"    405 "text/html"        "/" -X POST
check "path traversal"        403 "text/html"        "/../pom.xml" --path-as-is
check "encoded traversal"     403 "text/html"        "/images/%2e%2e/%2e%2e/pom.xml"

echo
echo "Repeated requests in one server run"
repeated_failures=0
for i in $(seq 1 10); do
  status="$(curl -s -o /dev/null -w '%{http_code}' "${BASE}/app/square?value=${i}")"
  [[ "$status" == "200" ]] || repeated_failures=$((repeated_failures + 1))
done
if [[ $repeated_failures -eq 0 ]]; then
  echo "  ok    ten consecutive requests succeeded"
else
  echo "  FAIL  $repeated_failures of 10 consecutive requests failed"
  failures=$((failures + repeated_failures))
fi

echo
if [[ $failures -eq 0 ]]; then
  echo "All checks passed."
else
  echo "$failures check(s) failed."
fi
exit $((failures > 0 ? 1 : 0))
