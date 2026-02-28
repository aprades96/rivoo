#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"

echo "=== Rivoo Development Setup ==="

# 1. MySQL setup
echo ""
echo "[1/3] Setting up MySQL databases..."
mysql -u root -p < "$PROJECT_ROOT/infrastructure/mysql/init-local.sql"
echo "MySQL databases created successfully."

# 2. Maven build
echo ""
echo "[2/3] Building project..."
cd "$PROJECT_ROOT"
mvn clean package -DskipTests
echo "Project built successfully."

# 3. Keycloak realm
echo ""
echo "[3/3] Keycloak setup..."
echo "Make sure Keycloak is running on port 9080."
echo "The realm 'rivoo' should be auto-imported from the realm JSON."
echo "If not, import manually: infrastructure/keycloak/rivoo-realm.json"

echo ""
echo "=== Setup Complete ==="
echo "Start services with: infrastructure/scripts/dev-start-all.sh"
