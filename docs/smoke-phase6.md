# Phase 6 Smoke Test — Weather

## Prerequisites

- Backend running.
- Dev auth enabled.

Example env:

```bash
export DATABASE_URL="jdbc:postgresql://localhost:5432/cotrip"
export DATABASE_USER="cotrip"
export DATABASE_PASSWORD=""
export DEV_AUTH_ENABLED=true
```

Start server (example port 8081):

```bash
cd /Users/ilya-nvk/StudioProjects/cotrip/backend
PORT=8081 ./gradlew run
```

## 1. Dev auth (owner)

```bash
curl -X POST http://localhost:8081/v1/auth/dev \
  -H 'Content-Type: application/json' \
  -d '{"googleId":"dev-1","name":"Owner User"}'
```

Save `accessToken` → `<OWNER_TOKEN>`

## 2. Create trip (owner)

```bash
curl -X POST http://localhost:8081/v1/trips \
  -H "Authorization: Bearer <OWNER_TOKEN>" \
  -H "Content-Type: application/json" \
  -d '{"title":"Paris","startDate":"2025-06-01","endDate":"2025-06-03","currencyCode":"EUR"}'
```

Save `id` → `<TRIP_ID>`

## 3. Refresh weather

```bash
curl -X POST "http://localhost:8081/v1/trips/<TRIP_ID>/weather/refresh?city=Paris" \
  -H "Authorization: Bearer <OWNER_TOKEN>"
```

Expected: list of forecast items.

## 4. Get weather

```bash
curl -H "Authorization: Bearer <OWNER_TOKEN>" \
  "http://localhost:8081/v1/trips/<TRIP_ID>/weather?city=Paris"
```

Expected: list contains the same dates as the trip.
