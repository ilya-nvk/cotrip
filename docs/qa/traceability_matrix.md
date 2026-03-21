# Матрица трассируемости: ТЗ -> ПМИ -> Код -> ВКР

## Формат
`Требование ТЗ -> Сценарий ПМИ -> Подтверждение в коде -> Раздел ВКР`

| Требование ТЗ | Сценарий ПМИ | Подтверждение в коде | Раздел ВКР |
|---|---|---|---|
| 4.1.1 Авторизация | 6.1, 6.2 | `android/app/src/main/java/nvk/cotrip/ui/auth/SignInViewModel.kt`; `android/app/src/main/java/nvk/cotrip/ui/settings/SettingsViewModel.kt` | 3.1 |
| 4.1.2 Поездки | 6.3 | `android/app/src/main/java/nvk/cotrip/ui/trip/list/TripsListViewModel.kt`; `android/app/src/main/java/nvk/cotrip/ui/trip/form/CreateTripViewModel.kt`; `android/app/src/main/java/nvk/cotrip/ui/trip/form/EditTripViewModel.kt` | 3.2 |
| 4.1.3 Приглашения | 6.4 | `android/app/src/main/java/nvk/cotrip/ui/invitation/InvitePeopleViewModel.kt`; `android/app/src/main/java/nvk/cotrip/ui/invitation/JoinTripViewModel.kt` | 3.2 |
| 4.1.4 Идеи и обсуждения | 6.5 | `android/app/src/main/java/nvk/cotrip/ui/idea/list/TripIdeasViewModel.kt`; `android/app/src/main/java/nvk/cotrip/ui/idea/details/IdeaDetailsViewModel.kt`; `backend/src/main/kotlin/nvk/cotrip/backend/ws/CommentsRoutes.kt` | 3.3 |
| 4.1.5 Маршрут | 6.6 | `android/app/src/main/java/nvk/cotrip/ui/itinerary/TripItineraryViewModel.kt`; `android/app/src/main/java/nvk/cotrip/ui/activity/form/CreateActivityViewModel.kt`; `android/app/src/main/java/nvk/cotrip/ui/activity/form/EditActivityViewModel.kt` | 3.3 |
| 4.1.5 Дни вне диапазона | 6.7 | `android/app/src/main/java/nvk/cotrip/ui/outofrangedays/OutOfRangeDaysViewModel.kt` | 3.3 |
| 4.1.6 Расходы и баланс | 6.8 | `android/app/src/main/java/nvk/cotrip/ui/expense/list/TripExpensesViewModel.kt`; `android/app/src/main/java/nvk/cotrip/ui/expense/details/ExpenseDetailsViewModel.kt` | 3.4 |
| 4.1.7 Погода | 6.9 | `android/app/src/main/java/nvk/cotrip/ui/forecast/TripForecastUiMapper.kt`; `android/app/src/main/java/nvk/cotrip/ui/forecast/TripForecastScreen.kt` | 3.5 |
| 4.1.8 Подсказки ИИ | 6.10 | `android/app/src/main/java/nvk/cotrip/ui/aisuggestions/RouteSuggestionsViewModel.kt` | 3.5 |
| 4.1.9 Настройки уведомлений | 6.11 | `android/app/src/main/java/nvk/cotrip/ui/settings/SettingsViewModel.kt`; `android/app/src/main/java/nvk/cotrip/notifications/SystemNotificationManager.kt` | 3.5 |
| 4.1.10 Офлайн-поведение | 6.12 | `android/app/src/main/java/nvk/cotrip/data/sync/SyncQueueRepository.kt`; `android/app/src/main/java/nvk/cotrip/data/sync/SyncWorker.kt`; `android/app/src/main/java/nvk/cotrip/data/sync/SyncPullRepository.kt` | 3.6 |
| 4.1.11 Локализация интерфейса | 6.13 | `android/app/src/main/res/values/strings.xml`; `android/app/src/main/res/values-ru/strings.xml`; `android/app/src/main/java/nvk/cotrip/ui/common/AppUiLocale.kt` | 3.1, 3.5 |
| 4.2 Индикаторы загрузки | 6.14 | `android/app/src/main/java/nvk/cotrip/ui/trip/form/CreateTripViewModel.kt`; `android/app/src/main/java/nvk/cotrip/ui/invitation/JoinTripViewModel.kt`; `android/app/src/main/java/nvk/cotrip/ui/idea/details/IdeaDetailsViewModel.kt` | 3.7 |
| 4.3 Ошибки и устойчивость | 6.14, 6.15 | `android/app/src/main/java/nvk/cotrip/ui/common/UiErrorMapper.kt`; `android/app/src/main/java/nvk/cotrip/data/sync/SyncStateStoreImpl.kt`; `android/app/src/main/java/nvk/cotrip/data/sync/SyncQueueRepository.kt`; `android/app/src/main/java/nvk/cotrip/data/sync/SyncWorker.kt` | 3.6, 3.7 |

## Зафиксированные ограничения версии
1. ПМИ проверяет только клиентские сценарии.
2. Пользовательское удаление ранее отправленных комментариев не входит в клиентский контур текущей версии; проверка прав удаления ведется на уровне серверного API.
3. Метрики Android+backend в ВКР используются как инженерный контроль разработки, не как замена клиентского ПМИ.
