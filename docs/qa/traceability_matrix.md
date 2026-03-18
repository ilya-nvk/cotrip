# Матрица трассируемости: ТЗ -> ПМИ -> Код -> ВКР

## Формат
`Требование ТЗ -> Сценарий ПМИ -> Подтверждение в коде -> Абзац ВКР`

| Требование ТЗ | Сценарий ПМИ | Подтверждение в коде | Раздел ВКР |
|---|---|---|---|
| 4.1.1 Авторизация | 6.1, 6.2 | `android/.../ui/auth/SignInViewModel.kt`, `android/.../ui/settings/SettingsViewModel.kt` | 3.1 |
| 4.1.2 Поездки | 6.3 | `android/.../ui/trip/list/TripsListViewModel.kt`, `android/.../ui/trip/form/CreateTripViewModel.kt`, `EditTripViewModel.kt` | 3.2 |
| 4.1.3 Приглашения | 6.4 | `android/.../ui/invitation/InvitePeopleViewModel.kt`, `JoinTripViewModel.kt` | 3.2 |
| 4.1.4 Идеи и обсуждения | 6.5 | `android/.../ui/idea/list/TripIdeasViewModel.kt`, `android/.../ui/idea/details/IdeaDetailsViewModel.kt`, `backend/.../ws/CommentsRoutes.kt` | 3.3 |
| 4.1.4 Удаление комментария только автором | 6.5 | `backend/.../routes/v1/CommentRoutes.kt` (`CommentRepository.softDelete(commentId, userId)`) | 3.3 |
| 4.1.5 Маршрут | 6.6 | `android/.../ui/itinerary/TripItineraryViewModel.kt`, `android/.../ui/activity/form/*` | 3.3 |
| 4.1.5 Дни вне диапазона | 6.7 | `android/.../ui/outofrangedays/OutOfRangeDaysViewModel.kt` | 3.3 |
| 4.1.6 Расходы и баланс | 6.8 | `android/.../ui/expense/list/TripExpensesViewModel.kt`, `android/.../ui/expense/details/ExpenseDetailsViewModel.kt` | 3.4 |
| 4.1.7 Погода | 6.9 | `android/.../ui/forecast/TripForecastUiMapper.kt`, `TripForecastScreen.kt` | 3.5 |
| 4.1.8 Подсказки ИИ | 6.10 | `android/.../ui/aisuggestions/RouteSuggestionsViewModel.kt` | 3.5 |
| 4.1.9 Настройки уведомлений | 6.11 | `android/.../ui/settings/SettingsViewModel.kt`, `android/.../notifications/SystemNotificationManager.kt` | 3.5 |
| 4.1.10 Офлайн-поведение | 6.12 | `android/.../data/sync/SyncQueueRepository.kt`, `SyncWorker.kt`, `SyncPullRepository.kt` | 3.6 |
| 4.1.11 Локализация интерфейса | 6.13 | `android/app/src/main/res/values/strings.xml`, `android/app/src/main/res/values-ru/strings.xml`, `android/.../ui/common/AppUiLocale.kt` | 3.1, 3.5 |
| 4.2 Индикаторы загрузки | 6.14 | UI state `isLoading` в `ViewModel`-ах и соответствующие экраны Compose | 3.7 |
| 4.3 Ошибки и устойчивость | 6.14, 6.15 | `android/.../ui/common/UiErrorMapper.kt`, локальный кэш/синк модули | 3.6, 3.7 |

## Зафиксированные ограничения версии
1. ПМИ проверяет только клиентские сценарии.
2. Для удаления комментариев в текущей версии не предъявляется требование мгновенной межклиентской доставки события удаления.
3. Метрики Android+backend в ВКР используются как инженерный контроль разработки, не как замена клиентского ПМИ.
