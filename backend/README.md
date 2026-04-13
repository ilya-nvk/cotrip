# Backend

Kotlin Ktor service for CoTrip.

Run locally:
```bash
cd backend
DATABASE_URL="jdbc:postgresql://localhost:5432/cotrip" \
DATABASE_USER="cotrip" \
DATABASE_PASSWORD="" \
DEV_AUTH_ENABLED=true \
./gradlew run
```

Main config comes from environment variables (JWT, DB, AI, media uploads).

Database schema is created automatically from `src/main/resources/db/schema.sql` on backend start.

Auth-related env vars:
- `JWT_SECRET` (required)
- `JWT_ACCESS_TTL_MINUTES` (optional, default `15`)
- `JWT_REFRESH_TTL_DAYS` (optional, default `30`)
- `AUTH_MAX_ACTIVE_SESSIONS` (optional, default `5`)
- `GOOGLE_ALLOWED_AUDIENCES` (comma-separated Google OAuth client IDs for `/v1/auth/google`)
- `GOOGLE_SERVER_CLIENT_ID` (legacy fallback for a single Google OAuth client ID when `GOOGLE_ALLOWED_AUDIENCES` is not set)

Android App Links (for opening invite links in the app) are served from:
`/.well-known/assetlinks.json`

Set these env vars on backend:
- `ANDROID_APP_LINK_PACKAGE` (for example `nvk.cotrip`)
- `ANDROID_APP_LINK_SHA256_CERT_FINGERPRINTS` (comma-separated SHA256 certificate fingerprints)
