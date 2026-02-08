# Phase 7 Smoke Test — AI Suggestions

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

## 3. Request AI suggestions

```bash
curl -X POST http://localhost:8081/v1/trips/<TRIP_ID>/ai/suggestions \
  -H "Authorization: Bearer <OWNER_TOKEN>" \
  -H "Content-Type: application/json" \
  -d '{
    "city":"Paris",
    "description":"Art and coffee",
    "typeOptions":["Museum","Cafe"],
    "timeOfDayOptions":["Morning","Evening"],
    "budgetOptions":["Budget","Mid-range"]
  }'
```

Save first `items[0].id` → `<SUGGESTION_ID>`

## 4. Save suggestion to ideas

```bash
curl -X POST http://localhost:8081/v1/ai/suggestions/<SUGGESTION_ID>/save-to-ideas \
  -H "Authorization: Bearer <OWNER_TOKEN>"
```

Expected: Idea DTO returned.

## 5. List ideas

```bash
curl -H "Authorization: Bearer <OWNER_TOKEN>" \
  http://localhost:8081/v1/trips/<TRIP_ID>/ideas
```

Expected: list contains the AI-created idea.
