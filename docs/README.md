# CoTrip Docs

## Required Environment (backend)

- `DATABASE_URL`
- `DATABASE_USER`
- `DATABASE_PASSWORD`
- `JWT_SECRET`
- `GOOGLE_ALLOWED_AUDIENCES` (comma-separated Google OAuth client IDs for Google sign-in)

Backend creates DB schema automatically from `backend/src/main/resources/db/schema.sql` on startup.

Optional:
- `DEV_AUTH_ENABLED=true` (for `/v1/auth/dev`)
- `JWT_ACCESS_TTL_MINUTES` (default `15`)
- `JWT_REFRESH_TTL_DAYS` (default `30`)
- `AUTH_MAX_ACTIVE_SESSIONS` (default `5`)
- `OPENWEATHER_API_KEY`
- `ALICE_AI_PROVIDER` (`mock` or `yandex`)
- `YC_AI_API_KEY`, `YC_FOLDER_ID`, `YC_AI_MODEL`
- `MEDIA_UPLOAD_DIR` (default `uploads`)
- `MEDIA_MAX_UPLOAD_BYTES` (default 10MB)
- `FIREBASE_PROJECT_ID`
- `FIREBASE_SERVICE_ACCOUNT_PATH` (path to Firebase Admin SDK JSON key)

## Operational Commands (server)

Restart backend:
```bash
sudo systemctl restart cotrip
```

Recent logs:
```bash
sudo journalctl -u cotrip -n 200 --no-pager
```

Live logs:
```bash
sudo journalctl -u cotrip -f -n 200
```

Health check:
```bash
curl -sS https://api.cotrip.site/health
```

## Notes

- Trip/profile image upload endpoint: `POST /v1/uploads/images` (`multipart/form-data`, field name: `file`)
- Uploaded images are served from `/uploads/*`.
