package nvk.cotrip.ui.ideaform

import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

interface IdeaFormContract {
    val state: StateFlow<IdeaFormState>
    val effects: SharedFlow<IdeaFormEffect>
    fun onEvent(event: IdeaFormEvent)
}
