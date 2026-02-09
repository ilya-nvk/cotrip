# Phase 8 Smoke Test — Notifications

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

## 2. Insert notification (SQL)

```sql
INSERT INTO notifications (user_id, type, payload)
VALUES ('<OWNER_ID>', 'trip_update', '{"message":"Trip updated"}');
```

## 3. List notifications

```bash
curl -H "Authorization: Bearer <OWNER_TOKEN>" \
  http://localhost:8081/v1/notifications
```

Save `items[0].id` → `<NOTIFICATION_ID>`

## 4. Mark as read

```bash
curl -X PATCH http://localhost:8081/v1/notifications/<NOTIFICATION_ID>/read \
  -H "Authorization: Bearer <OWNER_TOKEN>"
```

Expected: `204 No Content`.

## 5. Update notification settings

```bash
curl -X PATCH http://localhost:8081/v1/users/me/notification-settings \
  -H "Authorization: Bearer <OWNER_TOKEN>" \
  -H "Content-Type: application/json" \
  -d '{"items":[{"key":"trip_updates","enabled":true}]}'
```

Expected: settings list returned.
