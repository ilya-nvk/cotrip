package nvk.cotrip.ui.forecast

sealed interface TripForecastEffect {
    data class ShowToastRes(val resId: Int) : TripForecastEffect
}
