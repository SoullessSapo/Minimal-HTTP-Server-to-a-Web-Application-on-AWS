#!/usr/bin/env bash
#
# Runs ON the cloud instance. Installs the Java runtime, places the artifact under
# /opt/application-server and registers the application as a managed background service, so it
# keeps running after the administration session is closed.
#
# Usage (from the home directory of the instance, with the jar already uploaded):
#   sudo bash install-on-instance.sh application-server.jar [port] [app-env] [greeting-prefix]
#
# Defaults to port 8080 and APP_ENV=production, which is what keeps /shutdown unpublished.
# The script contains no credential and no address.

set -euo pipefail

JAR_SOURCE="${1:-application-server.jar}"
PORT="${2:-8080}"
APP_ENV="${3:-production}"
GREETING_PREFIX="${4:-Hello}"

APP_DIR="/opt/application-server"
SERVICE_NAME="application-server"
SERVICE_USER="appuser"

if [[ $EUID -ne 0 ]]; then
  echo "This script installs a system service and must be run with sudo." >&2
  exit 1
fi

if [[ ! -f "$JAR_SOURCE" ]]; then
  echo "Artifact not found: $JAR_SOURCE" >&2
  exit 1
fi

echo "==> Installing a Java runtime"
if command -v dnf >/dev/null 2>&1; then
  dnf install -y java-17-amazon-corretto-headless          # Amazon Linux 2023
elif command -v apt-get >/dev/null 2>&1; then
  apt-get update -y && apt-get install -y openjdk-17-jre-headless   # Ubuntu
else
  echo "No supported package manager was found; install a Java 17 runtime manually." >&2
  exit 1
fi
java -version

echo "==> Creating the service account and the application directory"
id -u "$SERVICE_USER" >/dev/null 2>&1 || useradd --system --shell /usr/sbin/nologin "$SERVICE_USER"
install -d -o "$SERVICE_USER" -g "$SERVICE_USER" "$APP_DIR"
install -o "$SERVICE_USER" -g "$SERVICE_USER" -m 0644 "$JAR_SOURCE" "$APP_DIR/application-server.jar"

echo "==> Registering the service (PORT=$PORT, APP_ENV=$APP_ENV)"
UNIT_TEMPLATE="$(dirname "$0")/application-server.service"
if [[ ! -f "$UNIT_TEMPLATE" ]]; then
  echo "The unit template $UNIT_TEMPLATE was not uploaded next to this script." >&2
  exit 1
fi

JAVA_BIN="$(command -v java)"
sed -e "s#^Environment=PORT=.*#Environment=PORT=${PORT}#" \
    -e "s#^Environment=APP_ENV=.*#Environment=APP_ENV=${APP_ENV}#" \
    -e "s#^Environment=GREETING_PREFIX=.*#Environment=GREETING_PREFIX=${GREETING_PREFIX}#" \
    -e "s#^ExecStart=.*#ExecStart=${JAVA_BIN} -jar ${APP_DIR}/application-server.jar#" \
    -e '/^;/d' \
    "$UNIT_TEMPLATE" > "/etc/systemd/system/${SERVICE_NAME}.service"

systemctl daemon-reload
systemctl enable --now "$SERVICE_NAME"
systemctl --no-pager --lines=10 status "$SERVICE_NAME" || true

echo "==> Verifying from inside the instance"
sleep 2
echo -n "    GET /pi       -> "; curl -fsS "http://localhost:${PORT}/pi" && echo
echo -n "    GET /config   -> "; curl -fsS "http://localhost:${PORT}/config" && echo
echo -n "    GET /shutdown -> "; curl -s -o /dev/null -w '%{http_code}\n' "http://localhost:${PORT}/shutdown"
echo "    (404 on /shutdown is the expected result when APP_ENV=production)"
echo "==> Done. Follow the logs with: journalctl -u ${SERVICE_NAME} -f"
