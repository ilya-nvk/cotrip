# Android Auth (Current)

## Overview
Android auth uses real Google Sign-In to obtain an `idToken`, then exchanges it with the backend via `POST /v1/auth/google`. The returned `accessToken` is stored in DataStore and attached to all API calls.

## Flow
1. User taps "Continue with Google".
2. The app launches Google Sign-In.
3. On success, we receive `idToken` and call `api.googleAuth(AuthGoogleRequest(idToken))`.
4. We persist `accessToken` in `DataStoreSessionStore`.
5. We navigate to `Trips` and clear `SignIn` from the back stack so Back does not return to auth.
6. On app start, `SignInViewModel` checks for an existing token and navigates to `Trips` immediately if present.

## Files
- `android/app/src/main/java/nvk/cotrip/ui/auth/SignInScreen.kt`
- `android/app/src/main/java/nvk/cotrip/ui/auth/SignInViewModel.kt`
- `android/app/src/main/java/nvk/cotrip/data/auth/DataStoreSessionStore.kt`
- `android/app/src/main/java/nvk/cotrip/data/network/AuthInterceptor.kt`
- `android/app/src/main/java/nvk/cotrip/data/network/CoTripApi.kt`

## Local Setup
Set the Google server client id in `android/local.properties`:
```
GOOGLE_SERVER_CLIENT_ID=YOUR_SERVER_CLIENT_ID
```
Gradle reads it and injects into `BuildConfig.GOOGLE_SERVER_CLIENT_ID`.
