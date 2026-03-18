package nvk.cotrip.ui.forecast

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import nvk.cotrip.R
import nvk.cotrip.data.network.dto.WeatherForecastDto
import nvk.cotrip.data.network.dto.WeatherForecastResponseDto
import nvk.cotrip.ui.theme.CoTripIcons
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class TripForecastUiMapperTest {
    private val context: Application = ApplicationProvider.getApplicationContext()

    @Test
    fun given_forecastResponse_when_mapDays_then_sortsItemsAndMapsMainUiFields() {
        // GIVEN
        val today = LocalDate.now()
        val response = WeatherForecastResponseDto(
            items = listOf(
                weather(
                    date = "bad-date",
                    tempMin = null,
                    tempMax = null,
                    description = null,
                    iconCode = null,
                ),
                weather(
                    date = today.plusDays(1).toString(),
                    tempMin = null,
                    tempMax = 11.7,
                    description = "light rain",
                    iconCode = "10n",
                ),
                weather(
                    date = today.toString(),
                    tempMin = 1.2,
                    tempMax = 5.4,
                    description = "clear sky",
                    iconCode = "01d",
                ),
            )
        )

        // WHEN
        val mapped = TripForecastUiMapper.mapDays(context, response)

        // THEN
        assertEquals(3, mapped.size)

        val todayItem = mapped.first()
        assertEquals(context.getString(R.string.trip_forecast_day_today), todayItem.title)
        assertNotNull(todayItem.subtitle)
        assertEquals(CoTripIcons.WeatherSunny, todayItem.icon)
        assertEquals("1° / 5°", todayItem.temp)
        assertTrue(todayItem.description.startsWith("Clear"))

        val tomorrowItem = mapped[1]
        assertEquals(context.getString(R.string.trip_forecast_day_tomorrow), tomorrowItem.title)
        assertEquals(CoTripIcons.WeatherRain, tomorrowItem.icon)
        assertEquals("12°", tomorrowItem.temp)

        val invalidDateItem = mapped.last()
        assertEquals("bad-date", invalidDateItem.title)
        assertNull(invalidDateItem.subtitle)
        assertEquals(context.getString(R.string.common_empty_placeholder), invalidDateItem.temp)
        assertEquals(context.getString(R.string.trip_forecast_description_missing), invalidDateItem.description)
    }

    @Test
    fun given_emptyOrFilledPayload_when_sourceAndLastUpdated_then_handleCorrectly() {
        // GIVEN
        val empty = WeatherForecastResponseDto()
        assertEquals(
            context.getString(R.string.trip_forecast_source_default),
            TripForecastUiMapper.source(context, empty),
        )
        assertEquals("", TripForecastUiMapper.lastUpdated(empty))

        // GIVEN
        val filled = WeatherForecastResponseDto(
            items = listOf(
                weather(source = "openweather", fetchedAt = "2026-03-16T09:00:00Z"),
                weather(source = "openweather", fetchedAt = "2026-03-16T10:30:00Z"),
            )
        )
        // WHEN / THEN
        assertEquals("openweather", TripForecastUiMapper.source(context, filled))
        assertTrue(TripForecastUiMapper.lastUpdated(filled).contains("2026"))
    }

    @Test
    fun given_hasSelectedCityAndResponse_when_coverageMessage_then_returnsExpectedPerBranch() {
        // GIVEN
        val baseResponse = WeatherForecastResponseDto(
            items = listOf(weather()),
            missingDates = listOf("2026-03-20"),
        )

        // WHEN / THEN
        assertEquals(
            context.getString(R.string.trip_forecast_coverage_city_missing),
            TripForecastUiMapper.coverageMessage(
                context = context,
                hasSelectedCity = false,
                response = baseResponse,
            ),
        )

        assertNull(
            TripForecastUiMapper.coverageMessage(
                context = context,
                hasSelectedCity = true,
                response = baseResponse.copy(missingDates = emptyList()),
            )
        )

        assertEquals(
            context.getString(R.string.trip_forecast_coverage_unavailable),
            TripForecastUiMapper.coverageMessage(
                context = context,
                hasSelectedCity = true,
                response = baseResponse.copy(items = emptyList()),
            ),
        )

        assertEquals(
            context.getString(R.string.trip_forecast_coverage_partial),
            TripForecastUiMapper.coverageMessage(
                context = context,
                hasSelectedCity = true,
                response = baseResponse.copy(availableTo = null),
            ),
        )

        val withDate = TripForecastUiMapper.coverageMessage(
            context = context,
            hasSelectedCity = true,
            response = baseResponse.copy(availableTo = "2026-03-21"),
        )
        assertTrue(withDate?.contains("2026") == true)
    }

    private fun weather(
        date: String = "2026-03-16",
        source: String = "openweather",
        fetchedAt: String = "2026-03-16T12:00:00Z",
        tempMin: Double? = 3.0,
        tempMax: Double? = 9.0,
        description: String? = "cloudy",
        iconCode: String? = "02d",
    ): WeatherForecastDto = WeatherForecastDto(
        id = "id-$date-$fetchedAt",
        tripId = "trip-1",
        city = "Paris",
        date = date,
        tempMin = tempMin,
        tempMax = tempMax,
        description = description,
        iconCode = iconCode,
        source = source,
        fetchedAt = fetchedAt,
    )
}
