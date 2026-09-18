package com.ably.example

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ColorVotingScreenTest {

  @get:Rule
  val composeTestRule = createAndroidComposeRule<MainActivity>()

  @Test
  fun incrementRedColor() {
    // Navigate to Color Voting tab
    composeTestRule.onNodeWithText("Color Voting").performClick()

    // Wait for the screen to load
    composeTestRule.waitForIdle()

    // Find and click the Vote button for Red color
    val redVoteButton = composeTestRule.onNodeWithTag("vote_button_red")

    // Capture initial count
    val initial = composeTestRule.onNodeWithTag("counter_red")
      .fetchSemanticsNode()
      .config[SemanticsProperties.Text].first().text.toInt()

    composeTestRule.waitUntil(timeoutMillis = 10_000) {
      SemanticsProperties.Disabled !in redVoteButton.fetchSemanticsNode().config
    }

    redVoteButton.performClick()

    // Wait for the counter to update with 5-seconds timeout
    composeTestRule.waitUntil(timeoutMillis = 5_000) {
      val updated = composeTestRule.onNodeWithTag("counter_red")
          .fetchSemanticsNode()
          .config[SemanticsProperties.Text].first().text.toInt()
      updated == initial + 1
    }
  }
}
