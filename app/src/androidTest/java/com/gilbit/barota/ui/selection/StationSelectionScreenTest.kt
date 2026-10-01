package com.gilbit.barota.ui.selection

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.gilbit.barota.data.model.Station
import com.gilbit.barota.ui.theme.SubwayBarotaTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class StationSelectionScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val gangnam = Station("gangnam", "강남", listOf("2호선"))
    private val seoul = Station("seoul", "서울역", listOf("1호선"))

    @Test
    fun historyShortcutsAndAllStationsHaveDistinctKeysAndSearchHidesSections() {
        val state = mutableStateOf(StationSelectionUiState(
            isLoading = false, selectionTarget = SelectionTarget.ORIGIN,
            filteredStations = listOf(gangnam, seoul), frequentStations = listOf(gangnam),
        ))
        composeRule.setContent {
            SubwayBarotaTheme(dynamicColor = false) { TestScreen(state.value) }
        }
        composeRule.onNodeWithText("자주 찾는 역").assertExists()
        composeRule.onNodeWithText("전체 역").assertExists()
        composeRule.onAllNodesWithText("강남").assertCountEquals(2)
        composeRule.runOnIdle { state.value = state.value.copy(query = "강남", filteredStations = listOf(gangnam)) }
        composeRule.onNodeWithText("자주 찾는 역").assertDoesNotExist()
        composeRule.onNodeWithText("전체 역").assertDoesNotExist()
        composeRule.onAllNodesWithText("강남").assertCountEquals(1)
    }

    @Test
    fun noHistoryShowsOnlyAllStationsAndRecompositionDoesNotSelectStation() {
        val state = mutableStateOf(StationSelectionUiState(
            isLoading = false, selectionTarget = SelectionTarget.DESTINATION,
            filteredStations = listOf(gangnam, seoul),
        ))
        var selections = 0
        composeRule.setContent {
            SubwayBarotaTheme(dynamicColor = false) {
                TestScreen(state.value, onStationSelected = { selections++ })
            }
        }
        composeRule.onNodeWithText("자주 찾는 역").assertDoesNotExist()
        composeRule.onNodeWithText("전체 역").assertExists()
        composeRule.runOnIdle { state.value = state.value.copy(query = "강남", filteredStations = listOf(gangnam)) }
        composeRule.runOnIdle { assertTrue(selections == 0) }
        composeRule.onNodeWithText("강남").performClick()
        composeRule.runOnIdle { assertTrue(selections == 1) }
    }

    @Test
    fun searchButtonIsDisabledUntilBothStationsAreSelected() {
        composeRule.setContent {
            SubwayBarotaTheme(dynamicColor = false) {
                TestScreen(StationSelectionUiState(isLoading = false, origin = gangnam))
            }
        }

        composeRule.onNodeWithText("도착 정보 확인").assertIsNotEnabled()
    }

    @Test
    fun completedSelectionCanContinueAndSwap() {
        var searched = false
        var swapped = false
        composeRule.setContent {
            SubwayBarotaTheme(dynamicColor = false) {
                TestScreen(
                    uiState = StationSelectionUiState(
                        isLoading = false,
                        origin = gangnam,
                        destination = seoul,
                    ),
                    onSearch = { searched = true },
                    onSwap = { swapped = true },
                )
            }
        }

        composeRule.onNodeWithText("도착 정보 확인").assertIsEnabled().performClick()
        composeRule.onNodeWithContentDescription("출발역과 도착역 맞바꾸기").performClick()

        assertTrue(searched)
        assertTrue(swapped)
    }

    @Composable
    private fun TestScreen(
        uiState: StationSelectionUiState,
        onSearch: () -> Unit = {},
        onSwap: () -> Unit = {},
        onStationSelected: (Station) -> Unit = {},
    ) {
        StationSelectionScreen(
            uiState = uiState,
            onOpenSelector = {},
            onCloseSelector = {},
            onQueryChange = {},
            onStationSelected = onStationSelected,
            onSwap = onSwap,
            onMessageShown = {},
            onSearch = onSearch,
        )
    }
}
