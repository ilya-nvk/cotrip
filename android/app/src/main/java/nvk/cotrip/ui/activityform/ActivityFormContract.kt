package nvk.cotrip.ui.activityform

import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

interface ActivityFormContract {
    val state: StateFlow<ActivityFormState>
    val effects: SharedFlow<ActivityFormEffect>
    fun onEvent(event: ActivityFormEvent)
}
