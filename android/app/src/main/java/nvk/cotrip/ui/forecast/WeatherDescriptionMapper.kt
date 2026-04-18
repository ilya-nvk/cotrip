package nvk.cotrip.ui.forecast

import android.content.Context
import nvk.cotrip.R

/**
 * Localizes known OpenWeather English description strings client-side so cached English payloads
 * still render correctly after locale or backend changes.
 */
object WeatherDescriptionMapper {
    fun localize(context: Context, raw: String?): String? {
        val text = raw?.trim()?.lowercase() ?: return null
        if (text.isEmpty()) return null
        val resId = when {
            text == "clear sky" || text == "clear" -> R.string.weather_desc_clear
            text == "few clouds" -> R.string.weather_desc_few_clouds
            text == "scattered clouds" -> R.string.weather_desc_scattered_clouds
            text == "broken clouds" || text == "overcast clouds" || text == "overcast" ->
                R.string.weather_desc_broken_clouds

            text == "mist" || text == "fog" -> R.string.weather_desc_mist
            text == "smoke" || text == "haze" -> R.string.weather_desc_haze
            text == "sand/dust whirls" || text == "dust" -> R.string.weather_desc_dust
            text == "volcanic ash" -> R.string.weather_desc_volcanic_ash
            text == "squalls" -> R.string.weather_desc_squalls
            text == "tornado" -> R.string.weather_desc_tornado

            text.startsWith("light rain") || text == "light intensity drizzle" ||
                text == "drizzle" || text == "light intensity drizzle rain" ->
                R.string.weather_desc_light_rain

            text.startsWith("moderate rain") || text == "rain" ->
                R.string.weather_desc_moderate_rain

            text.startsWith("heavy rain") || text == "heavy intensity rain" ||
                text == "very heavy rain" || text == "extreme rain" ->
                R.string.weather_desc_heavy_rain

            text.startsWith("freezing rain") -> R.string.weather_desc_freezing_rain
            text.startsWith("light snow") || text == "snow" -> R.string.weather_desc_light_snow
            text.startsWith("heavy snow") || text == "snowfall" -> R.string.weather_desc_heavy_snow
            text.startsWith("sleet") || text == "shower sleet" -> R.string.weather_desc_sleet

            text.startsWith("thunderstorm") || text.contains("thunderstorm with") ->
                R.string.weather_desc_thunderstorm

            text.startsWith("shower rain") -> R.string.weather_desc_shower_rain
            text.startsWith("light shower snow") || text.startsWith("shower snow") ->
                R.string.weather_desc_shower_snow

            else -> null
        }
        return if (resId != null) context.getString(resId) else raw
    }
}
