package com.example.policeapp.ui.main

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.example.policeapp.data.DataRepository
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** UI tests for [com.example.policeapp.ui.main.MainScreen]. */
class MainScreenTest {

  @get:Rule val composeTestRule = createAndroidComposeRule<ComponentActivity>()

  @Before
  fun setup() {
    composeTestRule.setContent { MainScreen(onSosCardClick = {}) }
  }

  @Test
  fun pendingRequests_areDisplayed() {
    DataRepository.getPendingRequests().forEach {
      composeTestRule.onNodeWithText(it.personName).assertExists()
    }
  }
}
