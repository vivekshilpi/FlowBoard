#!/bin/bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SONAR_HOST_URL="${SONAR_HOST_URL:-http://localhost:9000}"
SONAR_TOKEN="${SONAR_TOKEN:-}"

BACKEND_SERVICES=(
  "auth-service"
  "workspace-service"
  "board-service"
  "list-service"
  "card-service"
  "notification-service"
  "payment-service"
  "api-gateway"
  "eureka-server"
)

if [[ -z "$SONAR_TOKEN" ]]; then
  echo "SONAR_TOKEN is not set."
  echo "Example:"
  echo "  export SONAR_TOKEN=your_generated_token"
  echo "  ./run-sonar.sh"
  exit 1
fi

echo "Using SonarQube at $SONAR_HOST_URL"

for service in "${BACKEND_SERVICES[@]}"; do
  echo ""
  echo "Analyzing backend service: $service"
  (
    cd "$ROOT_DIR/$service"
    mvn clean verify sonar:sonar \
      -Dsonar.host.url="$SONAR_HOST_URL" \
      -Dsonar.token="$SONAR_TOKEN"
  )
done

if command -v sonar-scanner >/dev/null 2>&1; then
  echo ""
  echo "Preparing frontend coverage for flowboard-ui"
  (
    cd "$ROOT_DIR/../flowboard-ui"
    npm run test:coverage
    sonar-scanner \
      -Dsonar.host.url="$SONAR_HOST_URL" \
      -Dsonar.token="$SONAR_TOKEN"
  )
else
  echo ""
  echo "Skipping flowboard-ui Sonar upload because 'sonar-scanner' is not installed."
  echo "When ready, run:"
  echo "  cd \"$ROOT_DIR/../flowboard-ui\""
  echo "  npm run test:coverage"
  echo "  sonar-scanner -Dsonar.host.url=$SONAR_HOST_URL -Dsonar.token=YOUR_TOKEN"
fi

echo ""
echo "SonarQube analysis complete. Open $SONAR_HOST_URL/projects"
