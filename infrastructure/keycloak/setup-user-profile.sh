#!/bin/bash
# Applies the Keycloak User Profile configuration via Admin REST API
# This runs after Keycloak starts, since realm import doesn't support userProfile field

KEYCLOAK_URL="${KEYCLOAK_URL:-http://keycloak:9080}"
MAX_RETRIES=30
RETRY_INTERVAL=3

echo "Waiting for Keycloak to be ready at $KEYCLOAK_URL..."
for i in $(seq 1 $MAX_RETRIES); do
  if curl -sf "$KEYCLOAK_URL/realms/rivoo" > /dev/null 2>&1; then
    echo "Keycloak is ready!"
    break
  fi
  if [ "$i" -eq "$MAX_RETRIES" ]; then
    echo "ERROR: Keycloak not ready after $((MAX_RETRIES * RETRY_INTERVAL))s"
    exit 1
  fi
  sleep $RETRY_INTERVAL
done

# Wait extra time to ensure realm import has fully completed
echo "Waiting 5s for realm import to settle..."
sleep 5

# Get admin token
TOKEN=$(curl -sf -X POST "$KEYCLOAK_URL/realms/master/protocol/openid-connect/token" \
  -d "client_id=admin-cli" \
  -d "username=admin" \
  -d "password=admin" \
  -d "grant_type=password" | sed -n 's/.*"access_token":"\([^"]*\)".*/\1/p')

if [ -z "$TOKEN" ]; then
  echo "ERROR: Failed to get admin token"
  exit 1
fi

# Apply user profile and verify it stuck
MAX_APPLY_RETRIES=3
for attempt in $(seq 1 $MAX_APPLY_RETRIES); do
  echo "Applying User Profile configuration (attempt $attempt)..."
  HTTP_CODE=$(curl -sf -o /dev/null -w "%{http_code}" -X PUT \
    "$KEYCLOAK_URL/admin/realms/rivoo/users/profile" \
    -H "Authorization: Bearer $TOKEN" \
    -H "Content-Type: application/json" \
    -d @/opt/keycloak/rivoo-user-profile.json)

  if [ "$HTTP_CODE" = "200" ]; then
    # Verify it was actually applied
    sleep 1
    ATTRS=$(curl -sf -H "Authorization: Bearer $TOKEN" "$KEYCLOAK_URL/admin/realms/rivoo/users/profile")
    if echo "$ATTRS" | grep -q "tenant_id"; then
      echo "User Profile applied and verified successfully!"
      exit 0
    else
      echo "WARN: User Profile applied but tenant_id not found, retrying..."
      sleep 3
    fi
  else
    echo "WARN: Failed to apply User Profile (HTTP $HTTP_CODE), retrying..."
    sleep 3
  fi
done

echo "ERROR: Failed to apply User Profile after $MAX_APPLY_RETRIES attempts"
exit 1
