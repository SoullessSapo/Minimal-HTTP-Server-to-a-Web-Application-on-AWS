#!/usr/bin/env bash
#
# Runs on YOUR computer. Builds the artifact, uploads it to the cloud instance and installs it
# there as a managed background service configured for production.
#
# Nothing about the instance is stored in the repository: the address and the private key are read
# from the environment, and the key never leaves your machine.
#
#   export EC2_HOST=ec2-user@ec2-XX-XX-XX-XX.compute-1.amazonaws.com
#   export EC2_KEY=~/.ssh/lab-key.pem        # only when connecting with SSH
#   export APP_PORT=8080                     # optional, defaults to 8080
#   export APP_ENV=production                # optional, defaults to production
#   export GREETING_PREFIX=Hello             # optional
#   ./scripts/deploy-to-ec2.sh

set -euo pipefail

HOST="${EC2_HOST:?Set EC2_HOST to user@public-dns-of-the-instance}"
PORT="${APP_PORT:-8080}"
ENVIRONMENT="${APP_ENV:-production}"
PREFIX="${GREETING_PREFIX:-Hello}"
KEY="${EC2_KEY:-}"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"
JAR="$PROJECT_DIR/target/application-server.jar"

ssh_options=(-o StrictHostKeyChecking=accept-new)
if [[ -n "$KEY" ]]; then
  ssh_options+=(-i "$KEY")
fi

echo "==> Building and testing the artifact"
(cd "$PROJECT_DIR" && mvn -B clean package)

echo "==> Uploading the artifact and the installation files to $HOST"
scp "${ssh_options[@]}" \
  "$JAR" \
  "$SCRIPT_DIR/install-on-instance.sh" \
  "$SCRIPT_DIR/application-server.service" \
  "$HOST:~/"

echo "==> Installing and starting the service (APP_ENV=$ENVIRONMENT)"
ssh "${ssh_options[@]}" "$HOST" \
  "sudo bash install-on-instance.sh application-server.jar $PORT $ENVIRONMENT '$PREFIX'"

PUBLIC_HOST="${HOST#*@}"
echo
echo "==> Deployed. Verify it from your computer with:"
echo "    curl -i http://${PUBLIC_HOST}:${PORT}/pi"
echo "    curl -i \"http://${PUBLIC_HOST}:${PORT}/hello?name=Pedro\""
echo "    ./scripts/smoke-test.sh http://${PUBLIC_HOST}:${PORT}"
echo "    open  http://${PUBLIC_HOST}:${PORT}/"
echo
echo "    The security group must allow inbound TCP on port ${PORT}."
