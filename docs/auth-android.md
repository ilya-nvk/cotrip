# Android Auth (Current)

## Overview
Current Android auth is a dev flow backed by `POST /v1/auth/dev`. It stores the returned access token in DataStore and applies it to all subsequent API calls via `Authorization: Bearer <token>`.

## Flow
1. User taps "Sign in with Google" in the app (currently mapped to dev auth).
2. `SignInViewModel` calls `api.devAuth(...)`.
3. On success, we persist `accessToken` in `DataStoreSessionStore`.
4. We navigate to `Trips` and clear `SignIn` from the back stack so Back does not return to auth.
5. On app start, `SignInViewModel` checks for an existing token and navigates to `Trips` immediately if present.

## Files
- `android/app/src/main/java/nvk/cotrip/ui/auth/SignInViewModel.kt`
- `android/app/src/main/java/nvk/cotrip/data/auth/DataStoreSessionStore.kt`
- `android/app/src/main/java/nvk/cotrip/data/network/AuthInterceptor.kt`
- `android/app/src/main/java/nvk/cotrip/data/network/CoTripApi.kt`

## TODO: Real Google Auth
Planned changes when Google auth is implemented:
- Replace `api.devAuth(...)` with `api.googleAuth(...)`.
- Send the real Google `idToken` in `AuthGoogleRequest`.
- Keep token persistence and `AuthInterceptor` as-is.
- Keep the back-stack clearing behavior after successful auth.
