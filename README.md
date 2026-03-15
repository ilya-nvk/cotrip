# CoTrip

CoTrip is a trip planning app with:
- `android/` - Android client (Kotlin, Compose, Hilt)
- `backend/` - Kotlin Ktor API + PostgreSQL

## Local Run

Backend:
```bash
cd backend
DATABASE_URL="jdbc:postgresql://localhost:5432/cotrip" \
DATABASE_USER="cotrip" \
DATABASE_PASSWORD="" \
DEV_AUTH_ENABLED=true \
./gradlew run
```

Android:
```bash
cd android
./gradlew :app:assembleDebug
```

For Firebase push on Android, place `google-services.json` in `android/app/`.

## Docs

Minimal project docs are in `/docs/README.md`.

## License

Non-commercial only. See `/LICENSE`.
