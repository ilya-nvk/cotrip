# Phase 5 Smoke Test — Expenses

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
  -d '{"title":"Paris","startDate":"2025-06-01","endDate":"2025-06-03","currencyCode":"EUR"}'
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

## 4. Create expense

```bash
curl -X POST http://localhost:8081/v1/trips/<TRIP_ID>/expenses \
  -H "Authorization: Bearer <OWNER_TOKEN>" \
  -H "Content-Type: application/json" \
  -d '{
    "title":"Museum tickets",
    "amount":120,
    "currencyCode":"EUR",
    "status":"planned",
    "paidById":"<OWNER_ID>",
    "date":"2025-06-01",
    "splitType":"equally",
    "note":"Group purchase",
    "participants":[
      {"userId":"<OWNER_ID>","shareAmount":60,"isIncluded":true,"isPaid":false},
      {"userId":"<MEMBER_ID>","shareAmount":60,"isIncluded":true,"isPaid":false}
    ]
  }'
```

Save `id` → `<EXPENSE_ID>`

## 5. List expenses

```bash
curl -H "Authorization: Bearer <OWNER_TOKEN>" \
  http://localhost:8081/v1/trips/<TRIP_ID>/expenses
```

Expected: list contains `<EXPENSE_ID>`.

## 6. Update expense

```bash
curl -X PATCH http://localhost:8081/v1/expenses/<EXPENSE_ID> \
  -H "Authorization: Bearer <OWNER_TOKEN>" \
  -H "Content-Type: application/json" \
  -d '{"status":"paid","note":"Paid in cash"}'
```

Expected: status `paid`.

## 7. Get expense

```bash
curl -H "Authorization: Bearer <OWNER_TOKEN>" \
  http://localhost:8081/v1/expenses/<EXPENSE_ID>
```

Expected: expense details with participants.

## 8. Delete expense

```bash
curl -X DELETE http://localhost:8081/v1/expenses/<EXPENSE_ID> \
  -H "Authorization: Bearer <OWNER_TOKEN>"
```

Expected: `204 No Content`.
