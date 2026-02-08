# Phase 3 Smoke Test — Ideas

## Prerequisites

- Backend running.
- Dev auth enabled.
- Two users (owner + member).

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

## 1. Dev auth (owner + member)

```bash
curl -X POST http://localhost:8081/v1/auth/dev \
  -H 'Content-Type: application/json' \
  -d '{"googleId":"dev-1","name":"Owner User"}'
```

Save `accessToken` → `<OWNER_TOKEN>`
Save `user.id` → `<OWNER_ID>`

```bash
curl -X POST http://localhost:8081/v1/auth/dev \
  -H 'Content-Type: application/json' \
  -d '{"googleId":"dev-2","name":"Member User"}'
```

Save `accessToken` → `<MEMBER_TOKEN>`
Save `user.id` → `<MEMBER_ID>`

## 2. Create trip (owner)

```bash
curl -X POST http://localhost:8081/v1/trips \
  -H "Authorization: Bearer <OWNER_TOKEN>" \
  -H "Content-Type: application/json" \
  -d '{"title":"Paris","startDate":"2025-06-01","endDate":"2025-06-05","currencyCode":"EUR"}'
```

Save `id` → `<TRIP_ID>`

## 3. Invite + accept (member)

```bash
curl -X POST http://localhost:8081/v1/trips/<TRIP_ID>/invite \
  -H "Authorization: Bearer <OWNER_TOKEN>"
```

Save `token` → `<INVITE_TOKEN>`

```bash
curl -X POST http://localhost:8081/v1/invites/<INVITE_TOKEN>/accept \
  -H "Authorization: Bearer <MEMBER_TOKEN>"
```

## 4. Create idea (member)

```bash
curl -X POST http://localhost:8081/v1/trips/<TRIP_ID>/ideas \
  -H "Authorization: Bearer <MEMBER_TOKEN>" \
  -H "Content-Type: application/json" \
  -d '{"title":"Louvre","city":"Paris","costAmount":20,"costType":"per_person","website":"https://www.louvre.fr"}'
```

Save `id` → `<IDEA_ID>`

## 5. List ideas

```bash
curl -H "Authorization: Bearer <OWNER_TOKEN>" \
  http://localhost:8081/v1/trips/<TRIP_ID>/ideas
```

Expected: list contains `<IDEA_ID>`.

## 6. Approve idea (owner)

```bash
curl -X POST http://localhost:8081/v1/ideas/<IDEA_ID>/approve \
  -H "Authorization: Bearer <OWNER_TOKEN>"
```

Expected: status `approved`.

## 7. Update idea (member)

```bash
curl -X PATCH http://localhost:8081/v1/ideas/<IDEA_ID> \
  -H "Authorization: Bearer <MEMBER_TOKEN>" \
  -H "Content-Type: application/json" \
  -d '{"notes":"Buy tickets in advance"}'
```

Expected: updated notes.

## 8. Delete idea (member)

```bash
curl -X DELETE http://localhost:8081/v1/ideas/<IDEA_ID> \
  -H "Authorization: Bearer <MEMBER_TOKEN>"
```

Expected: `204 No Content`.
