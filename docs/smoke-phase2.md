# Phase 2 Smoke Test — Trip Members

## Prerequisites

- Backend running.
- Dev auth enabled.
- Two users in the system.

Example environment:

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

## 1. Create two users (dev auth)

```bash
curl -X POST http://localhost:8081/v1/auth/dev \
  -H 'Content-Type: application/json' \
  -d '{"googleId":"dev-1","name":"Owner User"}'
```

Save:
- `accessToken` → `<OWNER_TOKEN>`
- `user.id` → `<OWNER_ID>`

```bash
curl -X POST http://localhost:8081/v1/auth/dev \
  -H 'Content-Type: application/json' \
  -d '{"googleId":"dev-2","name":"Member User"}'
```

Save:
- `accessToken` → `<MEMBER_TOKEN>`
- `user.id` → `<MEMBER_ID>`

## 2. Create trip as owner

```bash
curl -X POST http://localhost:8081/v1/trips \
  -H "Authorization: Bearer <OWNER_TOKEN>" \
  -H "Content-Type: application/json" \
  -d '{"title":"Paris","startDate":"2025-06-01","endDate":"2025-06-05","currencyCode":"EUR"}'
```

Save `id` → `<TRIP_ID>`

## 3. Invite link and join as member

```bash
curl -X POST http://localhost:8081/v1/trips/<TRIP_ID>/invite \
  -H "Authorization: Bearer <OWNER_TOKEN>"
```

Save `token` → `<INVITE_TOKEN>`

```bash
curl -X POST http://localhost:8081/v1/invites/<INVITE_TOKEN>/accept \
  -H "Authorization: Bearer <MEMBER_TOKEN>"
```

## 4. List members

```bash
curl -H "Authorization: Bearer <OWNER_TOKEN>" \
  http://localhost:8081/v1/trips/<TRIP_ID>/members
```

Expected: two members (owner + member).

## 5. Member leaves (self delete)

```bash
curl -X DELETE http://localhost:8081/v1/trips/<TRIP_ID>/members/<MEMBER_ID> \
  -H "Authorization: Bearer <MEMBER_TOKEN>"
```

Expected: `204`.

## 6. Owner cannot be removed

```bash
curl -X DELETE http://localhost:8081/v1/trips/<TRIP_ID>/members/<OWNER_ID> \
  -H "Authorization: Bearer <OWNER_TOKEN>"
```

Expected: `403`.
