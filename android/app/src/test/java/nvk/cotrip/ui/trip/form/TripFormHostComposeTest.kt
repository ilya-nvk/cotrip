package nvk.cotrip.ui.trip.form

import android.app.Application
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.emptyFlow
import nvk.cotrip.R
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class TripFormHostComposeTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun given_tripFormHost_when_screenRenders_then_primaryAndCancelButtons_areRendered() {
        // GIVEN
        val context = ApplicationProvider.getApplicationContext<Application>()

        // WHEN
        composeRule.setContent {
            TripFormHost(
                titleRes = R.string.create_trip_title,
                primaryButtonRes = R.string.create_trip_primary_button,
                showAdvanced = false,
                state = TripFormState(
                    name = "Test trip",
                    startDate = LocalDate.of(2026, 6, 10),
                    endDate = LocalDate.of(2026, 6, 12),
                    canSubmit = true,
                ),
                effects = emptyFlow(),
                onEvent = {},
            )
        }

        val primaryNodes = composeRule.onAllNodesWithText(
            context.getString(R.string.create_trip_primary_button),
            substring = true,
            ignoreCase = true
        ).fetchSemanticsNodes()
        val cancelNodes = composeRule.onAllNodesWithText(
            context.getString(R.string.common_cancel),
            substring = true,
            ignoreCase = true
        ).fetchSemanticsNodes()

        // THEN
        assertTrue(primaryNodes.isNotEmpty())
        assertTrue(cancelNodes.isNotEmpty())
    }
}
