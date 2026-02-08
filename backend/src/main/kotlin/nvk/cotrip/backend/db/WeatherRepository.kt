package nvk.cotrip.backend.db

import java.sql.ResultSet
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.UUID

data class WeatherForecastRow(
    val id: String,
    val tripId: String,
    val city: String,
    val date: LocalDate,
    val tempMin: Double?,
    val tempMax: Double?,
    val description: String?,
    val iconCode: String?,
    val source: String,
    val fetchedAt: OffsetDateTime,
)

object WeatherRepository {
    fun list(tripId: String, city: String, start: LocalDate, end: LocalDate): List<WeatherForecastRow> = dbQuery { conn ->
        conn.prepareStatement(
            """
            SELECT id, trip_id, city, date, temp_min, temp_max, description, icon_code, source, fetched_at
            FROM weather_forecasts
            WHERE trip_id = ? AND city = ? AND date BETWEEN ? AND ?
            ORDER BY date ASC
            """.trimIndent()
        ).use { stmt ->
            stmt.setObject(1, UUID.fromString(tripId))
            stmt.setString(2, city)
            stmt.setObject(3, start)
            stmt.setObject(4, end)
            stmt.executeQuery().use { rs ->
                val result = mutableListOf<WeatherForecastRow>()
                while (rs.next()) {
                    result += mapForecast(rs)
                }
                result
            }
        }
    }

    fun upsert(
        tripId: String,
        city: String,
        date: LocalDate,
        tempMin: Double?,
        tempMax: Double?,
        description: String?,
        iconCode: String?,
        source: String,
    ): WeatherForecastRow = dbQuery { conn ->
        conn.prepareStatement(
            """
            INSERT INTO weather_forecasts (trip_id, city, date, temp_min, temp_max, description, icon_code, source)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (trip_id, city, date)
            DO UPDATE SET temp_min = EXCLUDED.temp_min,
                          temp_max = EXCLUDED.temp_max,
                          description = EXCLUDED.description,
                          icon_code = EXCLUDED.icon_code,
                          source = EXCLUDED.source,
                          fetched_at = now()
            RETURNING id, trip_id, city, date, temp_min, temp_max, description, icon_code, source, fetched_at
            """.trimIndent()
        ).use { stmt ->
            stmt.setObject(1, UUID.fromString(tripId))
            stmt.setString(2, city)
            stmt.setObject(3, date)
            if (tempMin == null) stmt.setNull(4, java.sql.Types.NUMERIC) else stmt.setDouble(4, tempMin)
            if (tempMax == null) stmt.setNull(5, java.sql.Types.NUMERIC) else stmt.setDouble(5, tempMax)
            stmt.setString(6, description)
            stmt.setString(7, iconCode)
            stmt.setString(8, source)
            stmt.executeQuery().use { rs ->
                rs.next()
                mapForecast(rs)
            }
        }
    }

    private fun mapForecast(rs: ResultSet): WeatherForecastRow {
        return WeatherForecastRow(
            id = rs.getObject("id", UUID::class.java).toString(),
            tripId = rs.getObject("trip_id", UUID::class.java).toString(),
            city = rs.getString("city"),
            date = rs.getObject("date", LocalDate::class.java),
            tempMin = rs.getObject("temp_min", java.math.BigDecimal::class.java)?.toDouble(),
            tempMax = rs.getObject("temp_max", java.math.BigDecimal::class.java)?.toDouble(),
            description = rs.getString("description"),
            iconCode = rs.getString("icon_code"),
            source = rs.getString("source"),
            fetchedAt = rs.getObject("fetched_at", OffsetDateTime::class.java),
        )
    }
}
