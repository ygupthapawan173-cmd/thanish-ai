package com.example

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.assertTextContains
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
        compose.onNodeWithTag("nav_settings").performClick()
        compose.onNodeWithTag("settings_command_tab").assertIsDisplayed()
        compose.onNodeWithTag("nav_home").performClick()
        compose.onNodeWithTag("home_command_tab").assertIsDisplayed()
    }
    @Test fun singularityHudRequiresConsentAndReview() {
        compose.onNodeWithTag("nav_omega").performClick()
        compose.onNodeWithTag("singularity_hud_open").performClick()
        compose.onNodeWithTag("singularity_hud").assertIsDisplayed()
        compose.onNodeWithTag("singularity_connect").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithTag("singularity_tab_1").performScrollTo().performClick()
        compose.onNodeWithTag("singularity_react_input").performScrollTo().performTextInput("check battery -> increase volume")
        compose.onNodeWithTag("singularity_review_chain").performScrollTo().performClick()
        compose.onNodeWithTag("singularity_react_status").performScrollTo().assertTextContains("Awaiting approval for step 1")
        compose.onNodeWithTag("singularity_tab_2").performScrollTo().performClick()
        compose.onNodeWithTag("singularity_evolution").performScrollTo().assertIsDisplayed()
    }
    @Test fun optInMemoryAndWakeControlsReachRealPreferenceRoutes() {
        compose.onNodeWithTag("nav_omega").performClick()
        compose.onNodeWithTag("unified_omega_voice").performClick()
        compose.onNodeWithTag("unified_background_ai").performScrollTo().assertIsOff().performClick().assertIsOn()
        compose.onNodeWithTag("unified_background_ai").performClick().assertIsOff()
        compose.onNodeWithTag("unified_omega_memory").performClick()
        compose.onNodeWithTag("unified_memory_context").performScrollTo().assertIsOff().performClick().assertIsOn()
        compose.onNodeWithTag("unified_memory_context").performClick().assertIsOff()
    }
}
