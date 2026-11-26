package com.example

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OmegaR105RuntimeTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    @Test fun launchShowsActualR105HomeAndOrb() {
        compose.onNodeWithTag("home_command_tab").assertIsDisplayed()
        compose.onNodeWithTag("r105_orb").assertIsDisplayed()
    }
}
