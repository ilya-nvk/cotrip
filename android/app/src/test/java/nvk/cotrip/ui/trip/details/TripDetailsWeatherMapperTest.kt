package nvk.cotrip.ui.trip.details

import nvk.cotrip.data.network.dto.ItineraryDayDto
import nvk.cotrip.data.network.dto.WeatherForecastDto
import nvk.cotrip.data.network.dto.WeatherForecastResponseDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TripDetailsWeatherMapperTest {
    @Test
    fun given_daysWithAndWithoutCoordinates_when_pickCity_then_prefersCityWithCoordinates() {
        // GIVEN
        val days = listOf(
            ItineraryDayDto(
                id = "d1",
                tripId = "t1",
                date = "2026-06-10",
                dayNumber = 1,
                city = "Paris",
                cityLat = null,
                cityLon = null,
                isOutOfRange = false,
            ),
            ItineraryDayDto(
                id = "d2",
                tripId = "t1",
                date = "2026-06-11",
                dayNumber = 2,
                city = "Lyon",
                cityLat = 45.0,
                cityLon = 4.0,
                isOutOfRange = false,
            ),
        )

        // WHEN
        val result = TripDetailsWeatherMapper.pickCity(days)

        // THEN
        assertEquals("Lyon", result)
    }

    @Test
    fun given_forecastResponse_when_mapResponse_then_mapsFiveDaysAndPartialNotice() {
        // GIVEN
        val response = WeatherForecastResponseDto(
            items = (1..6).map { idx ->
                WeatherForecastDto(
                    id = "w$idx",
                    tripId = "t1",
                    city = "Paris",
                    date = "2026-06-${10 + idx}",
                    tempMin = 10.2 + idx,
                    tempMax = 20.8 + idx,
                    iconCode = if (idx % 2 == 0) "01d" else "10n",
                    source = "openweather",
                    fetchedAt = "2026-06-10T10:00:00Z",
                )
            },
            missingDates = listOf("2026-06-25"),
        )

        // WHEN
        val card = TripDetailsWeatherMapper.mapResponse(
            city = "Paris",
            response = response,
        )

        // THEN
        assertEquals("Paris", card.city)
        assertEquals(5, card.days.size)
        assertEquals(WeatherCardNotice.Partial, card.notice)
        assertTrue(card.days.all { it.temp.contains("°") })
    }

    @Test
    fun given_specialCardMethods_when_called_then_haveExpectedNoticeValues() {
        // GIVEN / WHEN
        val missing = TripDetailsWeatherMapper.cityMissingCard()
        // THEN
        assertEquals(WeatherCardNotice.CityMissing, missing.notice)
        assertTrue(missing.days.isEmpty())

        // WHEN
        val unavailable = TripDetailsWeatherMapper.unavailableCard("Berlin")
        // THEN
        assertEquals(WeatherCardNotice.Unavailable, unavailable.notice)
        assertEquals("Berlin", unavailable.city)

        // WHEN
        val noData = TripDetailsWeatherMapper.mapResponse(
            city = "Rome",
            response = WeatherForecastResponseDto(items = emptyList()),
        )
        // THEN
        assertEquals(WeatherCardNotice.NoData, noData.notice)
        assertNotNull(noData)
    }
}
