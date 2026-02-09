# Phase 0 Smoke Test

## Prerequisites

- PostgreSQL running locally
- Backend configured to allow dev auth

Example environment:

```bash
export DATABASE_URL="jdbc:postgresql://localhost:5432/cotrip"
export DATABASE_USER="cotrip"
export DATABASE_PASSWORD=""
export DEV_AUTH_ENABLED=true
```

Start server:

```bash
cd /Users/ilya-nvk/StudioProjects/cotrip/backend
./gradlew run
```

## 1. Dev auth

```bash
curl -X POST http://localhost:8080/v1/auth/dev \
  -H 'Content-Type: application/json' \
  -d '{"googleId":"dev-1","name":"Dev User"}'
```

Extract `accessToken` and use it below.

## 2. Create trip

```bash
curl -X POST http://localhost:8080/v1/trips \
  -H "Authorization: Bearer <TOKEN>" \
  -H "Content-Type: application/json" \
  -d '{"title":"Paris","startDate":"2025-06-01","endDate":"2025-06-05","currencyCode":"EUR"}'
```

Extract `id` as `<TRIP_ID>`.

## 3. List trips

```bash
curl -H "Authorization: Bearer <TOKEN>" http://localhost:8080/v1/trips
```

## 4. Create invite link

```bash
curl -X POST http://localhost:8080/v1/trips/<TRIP_ID>/invite \
  -H "Authorization: Bearer <TOKEN>"
```

## Expected

- All commands return `200`.
- Flyway migration already applied.
- Invite response includes `token`, `url`, `expiresAt`.
