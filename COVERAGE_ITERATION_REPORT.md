# Coverage iteration report

## 1. Changed files

### Android (production)
- `android/app/src/main/java/nvk/cotrip/ui/expense/form/CreateExpenseViewModel.kt` – effects `extraBufferCapacity = 8`, removed `withContext(Dispatchers.IO)` in loadMembers / createExpense / deleteOldestAndRetry
- `android/app/src/main/java/nvk/cotrip/ui/expense/form/EditExpenseViewModel.kt` – same refactors (effects, no withContext in loadExpense / updateExpense / deleteExpense)
- `android/app/src/main/java/nvk/cotrip/ui/expense/details/ExpenseDetailsViewModel.kt` – `_effects` with `extraBufferCapacity = 8`, removed `withContext(Dispatchers.IO)` inside `apiCaller.call` in refreshExpense / markAsPaid / markParticipantPaid / unmarkParticipantPaid
- `android/app/src/main/java/nvk/cotrip/ui/expense/list/TripExpensesViewModel.kt` – `_effects` with `extraBufferCapacity = 8`, removed `withContext(Dispatchers.IO)` inside `apiCaller.call` in refreshExpenses
- `android/app/src/main/java/nvk/cotrip/ui/activity/form/CreateActivityViewModel.kt` – `_effects` with `extraBufferCapacity = 8`, removed `withContext(Dispatchers.IO)` inside `apiCaller.call` in loadTripMeta / createActivity / deleteOldestAndRetry / onLocationInputChanged
- `android/app/src/main/java/nvk/cotrip/ui/activity/form/EditActivityViewModel.kt` – same (effects, no withContext in loadActivity / updateActivity / onLocationInputChanged / deleteActivity)
- `android/app/src/main/java/nvk/cotrip/ui/activity/details/ActivityDetailsViewModel.kt` – `_effects` with `extraBufferCapacity = 8`, removed `withContext(Dispatchers.IO)` inside `apiCaller.call` in refreshActivity / deleteActivity
- `android/app/src/main/java/nvk/cotrip/ui/idea/form/CreateIdeaViewModel.kt` – `_effects` with `extraBufferCapacity = 8`, removed `withContext(Dispatchers.IO)` inside `apiCaller.call` in loadTripMeta / createIdea / deleteOldestAndRetry / onCityInputChanged
- `android/app/src/main/java/nvk/cotrip/ui/idea/form/EditIdeaViewModel.kt` – same (effects, no withContext in loadIdea / updateIdea / onCityInputChanged / deleteIdea)
- `android/app/src/main/java/nvk/cotrip/ui/trip/form/CreateTripViewModel.kt` – `_effects` with `extraBufferCapacity = 8`, removed `withContext(Dispatchers.IO)` inside `apiCaller.call` in createTrip / deleteOldestAndRetry / uploadCover; removed `withContext(Dispatchers.IO)` from `markTripCreationPending` for testability
- `android/app/src/main/java/nvk/cotrip/ui/trip/form/EditTripViewModel.kt` – `_effects` with `extraBufferCapacity = 8`, removed `withContext(Dispatchers.IO)` inside `apiCaller.call` in loadTrip / saveTrip / archiveTrip / deleteTrip / uploadCover
- `android/app/src/main/java/nvk/cotrip/data/network/ws/CommentsWebSocket.kt` – added `CommentEventsSource` and `CommentEventsSourceFactory` interfaces; `CommentsWebSocket` implements `CommentEventsSource`
- `android/app/src/main/java/nvk/cotrip/di/NetworkModule.kt` – `provideCommentEventsSourceFactory(okHttpClient, json)`
- `android/app/src/main/java/nvk/cotrip/ui/idea/list/TripIdeasViewModel.kt` – `_effects` with `extraBufferCapacity = 8`, removed `withContext(Dispatchers.IO)` inside `apiCaller.call` in refreshIdeas / selectDay; inject `CommentEventsSourceFactory`, use `commentEventsSource?.events ?: emptyFlow()` in observeSocket
- `android/app/src/main/java/nvk/cotrip/ui/idea/details/IdeaDetailsViewModel.kt` – `_effects` with `extraBufferCapacity = 8`, removed `withContext(Dispatchers.IO)` inside `apiCaller.call` in refreshDetails / selectDay / retryComment / updateStatus / deleteIdea; inject `CommentEventsSourceFactory`, same socket abstraction
- `android/app/src/main/java/nvk/cotrip/ui/trip/members/TripMembersViewModel.kt` – `_effects` with `extraBufferCapacity = 8`, removed `withContext(Dispatchers.IO)` inside `apiCaller.call` in refreshMembers and removeMember
- `android/app/src/main/java/nvk/cotrip/ui/aisuggestions/RouteSuggestionsViewModel.kt` – `_effects` with `extraBufferCapacity = 8`
- `android/app/src/main/java/nvk/cotrip/ui/auth/SignInViewModel.kt` – removed `withContext(Dispatchers.IO)` inside `apiCaller.call` in signInWithGoogle; after success `pushTokenSyncManager.syncCurrentToken()` wrapped in `runCatching` (no withContext) for testability

### Android (test)
- `android/app/src/test/java/nvk/cotrip/ui/expense/form/ExpenseFormTestDoubles.kt` – new (FakeNavigator, FakeTripRepository, FakeExpenseRepository, FakeUserRepository, DTO helpers); extended with `initialExpensesList` / `setExpenses` / `observeExpenses` for list tests
- `android/app/src/test/java/nvk/cotrip/ui/expense/form/CreateExpenseViewModelTest.kt` – new
- `android/app/src/test/java/nvk/cotrip/ui/expense/form/EditExpenseViewModelTest.kt` – new
- `android/app/src/test/java/nvk/cotrip/ui/expense/form/ExpenseFormScreenComposeTest.kt` – new
- `android/app/src/test/java/nvk/cotrip/ui/expense/details/ExpenseDetailsViewModelTest.kt` – new
- `android/app/src/test/java/nvk/cotrip/ui/expense/details/ExpenseDetailsScreenComposeTest.kt` – new
- `android/app/src/test/java/nvk/cotrip/ui/expense/list/TripExpensesViewModelTest.kt` – new
- `android/app/src/test/java/nvk/cotrip/ui/expense/list/TripExpensesScreenComposeTest.kt` – new
- `android/app/src/test/java/nvk/cotrip/ui/activity/ActivityTestDoubles.kt` – new (FakeNavigator, FakeTripRepository, FakeItineraryRepository, DTO helpers)
- `android/app/src/test/java/nvk/cotrip/ui/activity/form/CreateActivityViewModelTest.kt` – new
- `android/app/src/test/java/nvk/cotrip/ui/activity/form/EditActivityViewModelTest.kt` – new
- `android/app/src/test/java/nvk/cotrip/ui/activity/form/ActivityFormScreenComposeTest.kt` – new
- `android/app/src/test/java/nvk/cotrip/ui/activity/details/ActivityDetailsViewModelTest.kt` – new
- `android/app/src/test/java/nvk/cotrip/ui/activity/details/ActivityDetailsScreenComposeTest.kt` – new
- `android/app/src/test/java/nvk/cotrip/ui/idea/IdeaTestDoubles.kt` – new (FakeIdeaRepository, ideaDto helper)
- `android/app/src/test/java/nvk/cotrip/ui/idea/form/CreateIdeaViewModelTest.kt` – new
- `android/app/src/test/java/nvk/cotrip/ui/idea/form/EditIdeaViewModelTest.kt` – new
- `android/app/src/test/java/nvk/cotrip/ui/idea/form/IdeaFormScreenComposeTest.kt` – new
- `android/app/src/test/java/nvk/cotrip/ui/trip/form/TripFormTestDoubles.kt` – new (FakeImageUploadRepository)
- `android/app/src/test/java/nvk/cotrip/ui/trip/form/CreateTripViewModelTest.kt` – new
- `android/app/src/test/java/nvk/cotrip/ui/trip/form/EditTripViewModelTest.kt` – new
- `android/app/src/test/java/nvk/cotrip/ui/idea/IdeaTestDoubles.kt` – extended with `FakeCommentEventsSource`, `FakeCommentEventsSourceFactory`; `observeIdeas` now uses `ideaFlow.map`; `approveIdea`/`rejectIdea` return idea with status "approved"/"rejected"
- `android/app/src/test/java/nvk/cotrip/ui/idea/list/TripIdeasViewModelTest.kt` – new
- `android/app/src/test/java/nvk/cotrip/ui/idea/details/IdeaDetailsViewModelTest.kt` – new
- `android/app/src/test/java/nvk/cotrip/ui/trip/members/TripMembersViewModelTest.kt` – new
- `android/app/src/test/java/nvk/cotrip/ui/aisuggestions/AisuggestionsTestDoubles.kt` – new (FakeAiSuggestionsRepository, aiSuggestionDto helper)
- `android/app/src/test/java/nvk/cotrip/ui/aisuggestions/BuildRouteViewModelTest.kt` – new
- `android/app/src/test/java/nvk/cotrip/ui/aisuggestions/RouteSuggestionsViewModelTest.kt` – new
- `android/app/src/test/java/nvk/cotrip/ui/auth/SignInViewModelTest.kt` – new
- `android/app/src/test/java/nvk/cotrip/ui/trip/details/TripDetailsTestDoubles.kt` – extended with `removeMemberError` on TripDetailsFakeTripRepository for removeMember failure tests
- `android/app/src/test/java/nvk/cotrip/data/network/AuthInterceptorTest.kt` – new (AuthInterceptor: no token / blank token / Bearer header)
- `android/app/src/test/java/nvk/cotrip/data/auth/SessionCleanerTest.kt` – new (SessionCleaner: clearSession and clearSessionBlocking invoke sessionStore and all cache stores)
- `android/app/src/test/java/nvk/cotrip/data/network/ResponseExtTest.kt` – new (requireSuccess: success no throw, error throws HttpException)
- `android/app/src/test/java/nvk/cotrip/data/refresh/RefreshWorkerTest.kt` – new (doWork: offline→retry, noSession→success, allSuccess→success, syncFails→retry)

### Backend (test)
- `backend/src/test/kotlin/nvk/cotrip/backend/limits/LimitsTest.kt` – new
- `backend/src/test/kotlin/nvk/cotrip/backend/auth/AuthTokenServiceTest.kt` – new (refreshTokens blank/whitespace throws AuthFlowException; authenticateAccessToken null/invalid returns null)
- `backend/src/test/kotlin/nvk/cotrip/backend/ws/CommentsMessagesTest.kt` – new (CommentCreatedMessage and CommentCreatePayload round-trip serialization)

---

## 2. Tests run and result

### Android
- `./gradlew :app:testDebugUnitTest --tests "nvk.cotrip.ui.expense.*"` – **PASSED** (52 tests: form + details + list ViewModel and Compose)
- `./gradlew :app:testDebugUnitTest --tests "nvk.cotrip.ui.activity.*"` – **PASSED** (17 tests: form Create/Edit ViewModel, details ViewModel, ActivityDetailsScreenTest, Compose form/details)
- `./gradlew :app:testDebugUnitTest --tests "nvk.cotrip.ui.idea.form.*"` – **PASSED** (9 tests: Create/Edit ViewModel, IdeaFormScreenComposeTest)
- `./gradlew :app:testDebugUnitTest --tests "nvk.cotrip.ui.trip.form.*"` – **PASSED** (16 tests: TripDateRulesTest, TripFormHostComposeTest, CreateTripViewModelTest, EditTripViewModelTest)
- `./gradlew :app:testDebugUnitTest --tests "nvk.cotrip.ui.idea.*"` – **PASSED** (26 tests: form Create/Edit, IdeaFormScreenComposeTest, TripIdeasViewModelTest, IdeaDetailsViewModelTest)
- `./gradlew :app:testDebugUnitTest :app:jacocoDebugUnitTestReport` – **PASSED**
- `./gradlew :app:testDebugUnitTest --tests "nvk.cotrip.ui.trip.members.*" --tests "nvk.cotrip.ui.aisuggestions.*" --tests "nvk.cotrip.ui.auth.SignInViewModelTest"` – **PASSED** (TripMembers, BuildRoute, RouteSuggestions, SignIn ViewModel tests)
- `./gradlew :app:testDebugUnitTest --tests "nvk.cotrip.data.network.AuthInterceptorTest" --tests "nvk.cotrip.data.auth.SessionCleanerTest"` – **PASSED**
- `./gradlew :app:testDebugUnitTest --tests "nvk.cotrip.data.network.ResponseExtTest" --tests "nvk.cotrip.data.refresh.RefreshWorkerTest"` – **PASSED**
- `./gradlew :app:qualityCheck` – **FAILED** (coverage gate: requires LINE ≥ 90%, BRANCH ≥ 80%; current below)

### Backend
- `./gradlew test` (no Docker) – **PASSED** (container-tagged tests skipped via `assumeTrue`)
- `./gradlew test --tests "nvk.cotrip.backend.auth.AuthTokenServiceTest"` – **PASSED**
- `./gradlew test --tests "nvk.cotrip.backend.ws.CommentsMessagesTest"` – **PASSED**
- `./gradlew jacocoTestReport` – **PASSED**
- `./gradlew qualityCheck` – **FAILED** (coverage gate: LINE ≥ 90%, BRANCH ≥ 80%; current below)

---

## 3. Current coverage percentages

| Module   | LINE   | BRANCH |
|----------|--------|--------|
| Android  | 54%    | 29%    |
| Backend  | ~5%    | ~3%    |

(Android: from `jacocoDebugCoverageVerification` after adding ResponseExtTest and RefreshWorkerTest. Backend: from `jacocoTestCoverageVerification`; report total shows 5% lines / 3% branches.)

---

## 4. Container-tagged contract (backend)

- **CI=true and Docker unavailable:** `PostgresContainerSupport.ensureStarted()` calls `error(...)` → hard fail.
- **Local and Docker unavailable:** same code calls `assumeTrue(false, ...)` → container-tagged tests are skipped; other tests run.
- All DB-backed integration tests use `@PostgresIntegrationTest` (i.e. `@Tag("container")`): AuthRoutesIntegrationTest, DomainRoutesIntegrationTest, TripRoutesIntegrationTest, SyncRoutesTest.

No code changes were required; behaviour matches the desired contract.

---

## 5. Top zero / low-coverage areas and next steps

### Android (from existing structure)
- **Covered this iteration:** `ui/expense/details`, `ui/expense/list`; `ui/activity/form` (Create/Edit), `ui/activity/details`; `ui/idea/form` (Create/Edit ViewModel + IdeaFormScreenComposeTest); **`ui/trip/form`** (CreateTripViewModel, EditTripViewModel + TripFormTestDoubles, CreateTripViewModelTest, EditTripViewModelTest); **`ui/idea/list`** and **`ui/idea/details`** (CommentEventsSource abstraction, TripIdeasViewModel, IdeaDetailsViewModel refactors, TripIdeasViewModelTest, IdeaDetailsViewModelTest); **`ui/trip/members`** (TripMembersViewModel refactor + TripMembersViewModelTest); **`ui/aisuggestions`** (BuildRouteViewModelTest, RouteSuggestionsViewModelTest, AisuggestionsTestDoubles, RouteSuggestionsViewModel effects buffer); **`ui/auth`** (SignInViewModel refactor + SignInViewModelTest); **`data/network`** (AuthInterceptorTest, ResponseExtTest for requireSuccess); **`data/auth`** (SessionCleanerTest for SessionCleaner); **`data/refresh`** (RefreshWorkerTest for RefreshWorker: offline/noSession/success/retry); **backend auth** (AuthTokenServiceTest for blank refresh token and invalid access token paths); **backend ws** (CommentsMessagesTest for WsMessage serialization).
- **Still no/very low coverage:** data/cache, data/repository impls (except offline path), other backend routes/db as per JaCoCo report.
- **Next suggested:** Prioritise by JaCoCo “Missed Lines” (e.g. auth or other screens).

### Backend (from jacoco report)
- **Largest uncovered:** `nvk.cotrip.backend.routes.v1` (0% line, 283 classes), `nvk.cotrip.backend.db` (0% line, 74 classes), then `integrations`, `ws`, `auth`, `routes`, `plugins`, `limits`.
- **Some coverage:** `config` (83% line), `notifications` (57% line), `auth` (10% line), `integrations` (4% line).
- **Next suggested:** Add unit tests for pure logic in `auth` (JwtService, AuthTokenService), `limits` (done in this iteration), and any small helpers in `db` or routes that can be tested without a container.
