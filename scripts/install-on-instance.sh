#!/usr/bin/env bash
#
# Runs ON the EC2 instance. Installs the Java runtime, places the artifact under
# /opt/minimal-http-server and registers the application as a managed background service
# so that it keeps running after the administration session is closed.
#
# Usage (from the home directory of the instance, with the jar already uploaded):
#   sudo bash install-on-instance.sh minimal-http-server.jar [port]
#
# The script contains no credential and no address: everything it needs is a local file.

set -euo pipefail

JAR_SOURCE="${1:-minimal-http-server.jar}"
PORT="${2:-35000}"

APP_DIR="/opt/minimal-http-server"
SERVICE_NAME="minimal-http-server"
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
  # Amazon Linux 2023
  dnf install -y java-17-amazon-corretto-headless
elif command -v apt-get >/dev/null 2>&1; then
  # Ubuntu
  apt-get update -y
  apt-get install -y openjdk-17-jre-headless
else
  echo "No supported package manager was found; install a Java 17 runtime manually." >&2
  exit 1
fi
java -version

echo "==> Creating the service account and the application directory"
id -u "$SERVICE_USER" >/dev/null 2>&1 || useradd --system --shell /usr/sbin/nologin "$SERVICE_USER"
install -d -o "$SERVICE_USER" -g "$SERVICE_USER" "$APP_DIR"
install -o "$SERVICE_USER" -g "$SERVICE_USER" -m 0644 "$JAR_SOURCE" "$APP_DIR/minimal-http-server.jar"

echo "==> Registering the service on port $PORT"
UNIT_TEMPLATE="$(dirname "$0")/minimal-http-server.service"
if [[ ! -f "$UNIT_TEMPLATE" ]]; then
  echo "The unit template $UNIT_TEMPLATE was not uploaded next to this script." >&2
  exit 1
fi
JAVA_BIN="$(command -v java)"
sed -e "s#^Environment=PORT=.*#Environment=PORT=${PORT}#" \
    -e "s#^ExecStart=.*#ExecStart=${JAVA_BIN} -jar ${APP_DIR}/minimal-http-server.jar#" \
    -e '/^;/d' \
    "$UNIT_TEMPLATE" > "/etc/systemd/system/${SERVICE_NAME}.service"

systemctl daemon-reload
systemctl enable --now "$SERVICE_NAME"
systemctl --no-pager --lines=10 status "$SERVICE_NAME" || true

echo "==> Verifying the health service from inside the instance"
sleep 2
curl -fsS "http://localhost:${PORT}/health" && echo
echo "==> Done. Follow the logs with: journalctl -u ${SERVICE_NAME} -f"
