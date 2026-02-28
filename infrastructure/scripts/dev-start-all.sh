#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"

echo "=== Starting Rivoo Services ==="
echo "Make sure MySQL and Keycloak are running first."
echo ""

SERVICES=(
    "api-gateway:8080"
    "auth-service:8081"
    "salon-service:8082"
    "staff-service:8083"
    "client-service:8084"
    "appointment-service:8085"
    "notification-service:8086"
    "billing-service:8087"
    "admin-service:8088"
)

for entry in "${SERVICES[@]}"; do
    SERVICE="${entry%%:*}"
    PORT="${entry##*:}"
    echo "Starting $SERVICE on port $PORT..."
    cd "$PROJECT_ROOT/$SERVICE"
    mvn spring-boot:run -Dspring-boot.run.profiles=local &
    sleep 2
done

echo ""
echo "All services starting. Check health endpoints:"
for entry in "${SERVICES[@]}"; do
    SERVICE="${entry%%:*}"
    PORT="${entry##*:}"
    echo "  $SERVICE: http://localhost:$PORT/actuator/health"
done

wait
