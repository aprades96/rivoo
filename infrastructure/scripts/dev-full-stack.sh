#!/usr/bin/env bash
set -euo pipefail

###############################################################################
# Rivoo — Full Stack Local Development Launcher
#
# Arranca: MySQL check → Keycloak → Backend (9 servicios) → Frontend
# Uso: bash infrastructure/scripts/dev-full-stack.sh
# Para parar todo: Ctrl+C (mata todos los procesos hijo)
###############################################################################

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
FRONTEND_ROOT="$(cd "$PROJECT_ROOT/../rivoo-frontend" 2>/dev/null && pwd || echo "")"
KEYCLOAK_HOME="${KEYCLOAK_HOME:-E:/keycloak-26.0.6}"

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

log()  { echo -e "${BLUE}[rivoo]${NC} $1"; }
ok()   { echo -e "${GREEN}  ✓${NC} $1"; }
warn() { echo -e "${YELLOW}  !${NC} $1"; }
fail() { echo -e "${RED}  ✗${NC} $1"; exit 1; }

# Cleanup on exit
PIDS=()
cleanup() {
    log "Parando todos los procesos..."
    for pid in "${PIDS[@]}"; do
        kill "$pid" 2>/dev/null || true
    done
    wait 2>/dev/null
    log "Todos los procesos parados."
}
trap cleanup EXIT INT TERM

###############################################################################
# 1. Prerequisites check
###############################################################################
log "Verificando prerequisitos..."

# Source bashrc for mvn/mysql in PATH
source ~/.bashrc 2>/dev/null || true

# Java
if ! command -v java &>/dev/null; then
    fail "Java no encontrado en PATH"
fi
ok "Java $(java -version 2>&1 | head -1 | awk -F'"' '{print $2}')"

# Maven
if ! command -v mvn &>/dev/null; then
    fail "Maven no encontrado en PATH"
fi
ok "Maven $(mvn -version 2>&1 | head -1 | awk '{print $3}')"

# Node (for frontend)
if ! command -v node &>/dev/null; then
    warn "Node.js no encontrado — frontend no se arrancara"
fi

###############################################################################
# 2. MySQL check
###############################################################################
log "Verificando MySQL..."

if mysql -u rivoo -privoo123 -e "SELECT 1" &>/dev/null; then
    ok "MySQL conecta con usuario rivoo"
else
    fail "MySQL no accesible. Asegurate de que el servicio MySQL80 esta corriendo."
fi

# Check databases exist
DB_COUNT=$(mysql -u rivoo -privoo123 -N -e "SELECT COUNT(*) FROM information_schema.SCHEMATA WHERE SCHEMA_NAME IN ('auth_db','salon_db','staff_db','client_db','appointment_db','notification_db','billing_db')" 2>/dev/null)
if [ "$DB_COUNT" = "7" ]; then
    ok "7 bases de datos encontradas"
else
    warn "Solo $DB_COUNT de 7 BDs encontradas. Ejecuta: mysql -u root -proot < infrastructure/mysql/init-local.sql"
fi

###############################################################################
# 3. Keycloak
###############################################################################
log "Verificando Keycloak..."

if curl -sf http://localhost:9080/health/ready &>/dev/null; then
    ok "Keycloak ya esta corriendo en :9080"
else
    if [ -d "$KEYCLOAK_HOME" ]; then
        log "Arrancando Keycloak..."
        cd "$KEYCLOAK_HOME"
        bin/kc.bat start-dev --http-port=9080 --import-realm &>/dev/null &
        PIDS+=($!)

        # Wait for Keycloak to be ready (max 60s)
        for i in $(seq 1 60); do
            if curl -sf http://localhost:9080/health/ready &>/dev/null; then
                ok "Keycloak arrancado en :9080 (${i}s)"
                break
            fi
            if [ "$i" = "60" ]; then
                fail "Keycloak no arranco en 60s"
            fi
            sleep 1
        done
    else
        fail "Keycloak no encontrado en $KEYCLOAK_HOME"
    fi
fi

###############################################################################
# 4. Backend services
###############################################################################
log "Arrancando backend (9 servicios)..."
cd "$PROJECT_ROOT"

# Build first (skip tests for speed)
log "Compilando proyecto..."
mvn clean package -DskipTests -q 2>&1 | tail -1
ok "Build completado"

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
    log "  Arrancando $SERVICE (:$PORT)..."
    cd "$PROJECT_ROOT/$SERVICE"
    mvn spring-boot:run -Dspring-boot.run.profiles=local &>/dev/null &
    PIDS+=($!)
    sleep 1
done

# Wait for gateway to be healthy (indicates all routes registered)
log "Esperando a que el gateway este listo..."
for i in $(seq 1 90); do
    if curl -sf http://localhost:8080/actuator/health &>/dev/null; then
        ok "API Gateway listo en :8080 (${i}s)"
        break
    fi
    if [ "$i" = "90" ]; then
        warn "Gateway no respondio en 90s — algunos servicios pueden tardar mas"
    fi
    sleep 1
done

# Quick health check on key services
for entry in "salon-service:8082" "staff-service:8083" "appointment-service:8085"; do
    SERVICE="${entry%%:*}"
    PORT="${entry##*:}"
    if curl -sf "http://localhost:$PORT/actuator/health" &>/dev/null; then
        ok "$SERVICE listo"
    else
        warn "$SERVICE aun arrancando..."
    fi
done

###############################################################################
# 5. Frontend
###############################################################################
if [ -n "$FRONTEND_ROOT" ] && command -v node &>/dev/null; then
    log "Arrancando frontend..."
    cd "$FRONTEND_ROOT"
    npm run dev &
    PIDS+=($!)
    sleep 3
    ok "Frontend en http://localhost:3000"
else
    warn "Frontend no arrancado (directorio no encontrado o Node no disponible)"
fi

###############################################################################
# Summary
###############################################################################
echo ""
echo -e "${GREEN}=== Rivoo Full Stack Running ===${NC}"
echo ""
echo "  Keycloak:  http://localhost:9080  (admin: admin/admin)"
echo "  Gateway:   http://localhost:8080"
echo "  Frontend:  http://localhost:3000"
echo ""
echo "  Flujo de prueba:"
echo "    1. Abre http://localhost:3000"
echo "    2. Click 'Iniciar sesion'"
echo "    3. Login en Keycloak con tu usuario de test"
echo "    4. Deberias ver la Today view"
echo ""
echo -e "${YELLOW}  Ctrl+C para parar todo${NC}"
echo ""

# Keep script running
wait
