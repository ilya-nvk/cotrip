# Phase 1 Smoke Test — Comments (REST + WS)

## Prerequisites

- Backend running.
- PostgreSQL running.
- Dev auth enabled.

Example environment:

```bash
export DATABASE_URL="jdbc:postgresql://localhost:5432/cotrip"
export DATABASE_USER="cotrip"
export DATABASE_PASSWORD=""
export DEV_AUTH_ENABLED=true
```

Start server (if port 8080 is busy, set PORT=8081):

```bash
cd /Users/ilya-nvk/StudioProjects/cotrip/backend
PORT=8081 ./gradlew run
```

## 1. Dev auth

```bash
curl -X POST http://localhost:8081/v1/auth/dev \
  -H 'Content-Type: application/json' \
  -d '{"googleId":"dev-1","name":"Dev User"}'
```

Extract:
- `accessToken` → `<TOKEN>`
- `user.id` → `<USER_ID>`

## 2. Create trip

```bash
curl -X POST http://localhost:8081/v1/trips \
  -H "Authorization: Bearer <TOKEN>" \
  -H "Content-Type: application/json" \
  -d '{"title":"Paris","startDate":"2025-06-01","endDate":"2025-06-05","currencyCode":"EUR"}'
```

Extract `id` → `<TRIP_ID>`

## 3. Insert an idea (SQL)

There is no Ideas API yet, so insert a row directly:

```sql
INSERT INTO ideas (id, trip_id, author_id, title, status)
VALUES (gen_random_uuid(), '<TRIP_ID>', '<USER_ID>', 'Louvre', 'pending')
RETURNING id;
```

Save returned `id` → `<IDEA_ID>`

## 4. GET comments (should be empty)

```bash
curl -H "Authorization: Bearer <TOKEN>" \
  http://localhost:8081/v1/ideas/<IDEA_ID>/comments
```

Expected: `items` is empty.

## 5. WS create comment

Connect with any WS client (e.g. `wscat`):

```bash
wscat -c "ws://localhost:8081/v1/ws/trips/<TRIP_ID>/comments?token=<TOKEN>"
```

Send:

```json
{"type":"comment.create","payload":{"ideaId":"<IDEA_ID>","body":"First comment"}}
```

Expected broadcast:

```json
{"type":"comment.created","payload":{"id":"...","ideaId":"<IDEA_ID>","authorId":"<USER_ID>","body":"First comment","createdAt":"..."}}
```

## 6. GET comments again

```bash
curl -H "Authorization: Bearer <TOKEN>" \
  http://localhost:8081/v1/ideas/<IDEA_ID>/comments
```

Expected: `items` contains 1 comment.

## 7. DELETE comment

```bash
curl -X DELETE -H "Authorization: Bearer <TOKEN>" \
  http://localhost:8081/v1/comments/<COMMENT_ID>
```

Expected: `204 No Content`, and comments list returns empty.
