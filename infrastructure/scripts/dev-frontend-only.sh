#!/usr/bin/env bash
set -euo pipefail

###############################################################################
# Rivoo — Frontend Only (assumes backend + Keycloak already running)
# Uso: bash infrastructure/scripts/dev-frontend-only.sh
###############################################################################

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
FRONTEND_ROOT="$(cd "$PROJECT_ROOT/../rivoo-frontend" 2>/dev/null && pwd || echo "")"

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

log()  { echo -e "${BLUE}[rivoo]${NC} $1"; }
ok()   { echo -e "${GREEN}  ✓${NC} $1"; }
warn() { echo -e "${YELLOW}  !${NC} $1"; }
fail() { echo -e "${RED}  ✗${NC} $1"; exit 1; }

# Check prerequisites
log "Verificando servicios..."

if curl -sf http://localhost:9080/health/ready &>/dev/null; then
    ok "Keycloak (:9080)"
else
    fail "Keycloak no esta corriendo en :9080"
fi

if curl -sf http://localhost:8080/actuator/health &>/dev/null; then
    ok "API Gateway (:8080)"
else
    fail "API Gateway no esta corriendo en :8080. Arranca el backend primero."
fi

# Check key services
for entry in "salon-service:8082" "staff-service:8083" "appointment-service:8085"; do
    SERVICE="${entry%%:*}"
    PORT="${entry##*:}"
    if curl -sf "http://localhost:$PORT/actuator/health" &>/dev/null; then
        ok "$SERVICE (:$PORT)"
    else
        warn "$SERVICE (:$PORT) no responde — algunas funciones pueden no estar disponibles"
    fi
done

# Start frontend
if [ -z "$FRONTEND_ROOT" ]; then
    fail "rivoo-frontend no encontrado en $PROJECT_ROOT/../rivoo-frontend"
fi

log "Arrancando frontend..."
cd "$FRONTEND_ROOT"

echo ""
echo -e "${GREEN}=== Frontend listo ===${NC}"
echo "  http://localhost:3000"
echo ""

npm run dev
