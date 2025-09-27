package app.cotrip.ui.home

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HomeScreenTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun homeScreen_displaysHomeTitle() {
        composeTestRule.onNodeWithText("CoTrip Home").assertExists()
    }

    @Test
    fun homeScreen_refreshButtonClickable() {
        composeTestRule.onNodeWithText("Refresh Trips").performClick()
    }
}
