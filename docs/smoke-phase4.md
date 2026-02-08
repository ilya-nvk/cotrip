# Phase 4 Smoke Test — Itinerary

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
Save `user.id` → `<OWNER_ID>`

## 2. Create trip (owner)

```bash
curl -X POST http://localhost:8081/v1/trips \
  -H "Authorization: Bearer <OWNER_TOKEN>" \
  -H "Content-Type: application/json" \
  -d '{"title":"Paris","startDate":"2025-06-01","endDate":"2025-06-03","currencyCode":"EUR"}'
```

Save `id` → `<TRIP_ID>`

## 3. Get itinerary

```bash
curl -H "Authorization: Bearer <OWNER_TOKEN>" \
  http://localhost:8081/v1/trips/<TRIP_ID>/itinerary
```

Save first `items[0].id` → `<DAY_ID>`.

## 4. Update day city

```bash
curl -X PATCH http://localhost:8081/v1/itinerary/days/<DAY_ID> \
  -H "Authorization: Bearer <OWNER_TOKEN>" \
  -H "Content-Type: application/json" \
  -d '{"city":"Paris"}'
```

Expected: `204 No Content`.

## 5. Add activity

```bash
curl -X POST http://localhost:8081/v1/itinerary/days/<DAY_ID>/activities \
  -H "Authorization: Bearer <OWNER_TOKEN>" \
  -H "Content-Type: application/json" \
  -d '{"title":"Louvre","timeText":"10:00","locationName":"Louvre"}'
```

Save `id` → `<ACTIVITY_ID>`.

## 6. Update activity

```bash
curl -X PATCH http://localhost:8081/v1/itinerary/activities/<ACTIVITY_ID> \
  -H "Authorization: Bearer <OWNER_TOKEN>" \
  -H "Content-Type: application/json" \
  -d '{"notes":"Buy tickets"}'
```

Expected: notes updated.

## 7. Reorder activities

Create a second activity first and save its id as `<ACTIVITY_ID_2>`, then:

```bash
curl -X POST http://localhost:8081/v1/itinerary/days/<DAY_ID>/activities/reorder \
  -H "Authorization: Bearer <OWNER_TOKEN>" \
  -H "Content-Type: application/json" \
  -d '{"orderedIds":["<ACTIVITY_ID_2>","<ACTIVITY_ID>"]}'
```

Expected: `204 No Content`.

## 8. Trim out-of-range days (keep)

```bash
curl -X POST http://localhost:8081/v1/trips/<TRIP_ID>/itinerary/trim-out-of-range \
  -H "Authorization: Bearer <OWNER_TOKEN>" \
  -H "Content-Type: application/json" \
  -d '{"action":"keep","dayIds":["<DAY_ID>"]}'
```

Expected: other days are marked out of range.

## 9. Delete activity

```bash
curl -X DELETE http://localhost:8081/v1/itinerary/activities/<ACTIVITY_ID> \
  -H "Authorization: Bearer <OWNER_TOKEN>"
```

Expected: `204 No Content`.
