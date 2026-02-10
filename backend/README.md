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
