# Backend

Kotlin Ktor service for CoTrip.

## Run locally

Prerequisites: **PostgreSQL** with a database and a user the app can use. Create them first (names below are examples):

```sql
CREATE USER cotrip WITH PASSWORD 'your-password';
CREATE DATABASE cotrip OWNER cotrip;
```

Configuration is read from **environment variables** and from `src/main/resources/application.conf` (see `ConfigLoader.kt`).  
**Required** for a typical run: `DATABASE_URL`, `DATABASE_USER`, `DATABASE_PASSWORD` — a **non-empty** password in `DATABASE_PASSWORD` (an empty value is treated as *missing* and the app will not start; for an actually empty password use `ktor.db.password` in `application.conf` instead of the env var).

Example:

```bash
cd backend
export DATABASE_URL="jdbc:postgresql://localhost:5432/cotrip"
export DATABASE_USER="cotrip"
export DATABASE_PASSWORD="your-password"
# Optional: dev login without Google (POST /v1/auth/dev)
export DEV_AUTH_ENABLED=true
# Optional but recommended in non-local deployments:
# export JWT_SECRET="long-random-secret"
./gradlew run
```

The server listens on `http://0.0.0.0:8080` by default (override with `PORT`).

Main config comes from environment variables (JWT, DB, AI, media uploads, etc.).

The database schema is created automatically from `src/main/resources/db/schema.sql` on backend start.

## Auth-related env vars

- `JWT_SECRET` — optional; if unset, a default is taken from `application.conf` (see `ConfigLoader`); set explicitly in production.
- `JWT_ACCESS_TTL_MINUTES` (optional, default `15`)
- `JWT_REFRESH_TTL_DAYS` (optional, default `30`)
- `AUTH_MAX_ACTIVE_SESSIONS` (optional, default `5`)
- `GOOGLE_ALLOWED_AUDIENCES` — comma-separated Google OAuth client IDs for `/v1/auth/google`
- `GOOGLE_SERVER_CLIENT_ID` — legacy fallback for a single Google OAuth client ID when `GOOGLE_ALLOWED_AUDIENCES` is not set

`DEV_AUTH_ENABLED=true` enables `POST /v1/auth/dev` for local testing without a real Google `idToken`.

## Android App Links

Served at `/.well-known/assetlinks.json` when these are set:

- `ANDROID_APP_LINK_PACKAGE` (for example `nvk.cotrip`)
- `ANDROID_APP_LINK_SHA256_CERT_FINGERPRINTS` (comma-separated SHA-256 certificate fingerprints; if unset, the endpoint may return an empty array)
