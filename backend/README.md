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

Android App Links (for opening invite links in the app) are served from:
`/.well-known/assetlinks.json`

Set these env vars on backend:
- `ANDROID_APP_LINK_PACKAGE` (for example `nvk.cotrip`)
- `ANDROID_APP_LINK_SHA256_CERT_FINGERPRINTS` (comma-separated SHA256 certificate fingerprints)
