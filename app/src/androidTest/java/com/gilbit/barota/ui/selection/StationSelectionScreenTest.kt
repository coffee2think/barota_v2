package com.gilbit.barota.ui.selection

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.gilbit.barota.domain.RouteShortcut
import com.gilbit.barota.data.model.Station
import com.gilbit.barota.ui.theme.SubwayBarotaTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class StationSelectionScreenTest {
    @Test fun shortcutSaveButtonDoesNotLaunchAndRouteRemainsReachableByScrolling() {
        val route = RouteShortcut(gangnam, seoul)
        var launches = 0
        var saves = 0
        composeRule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 1.5f)) {
            SubwayBarotaTheme(dynamicColor = false) {
                StationSelectionScreen(
                    uiState = StationSelectionUiState(isLoading = false, recommendedRoutes = listOf(route)),
                    onOpenSelector = {}, onCloseSelector = {}, onQueryChange = {}, onStationSelected = {},
                    onSwap = {}, onMessageShown = {}, onSearch = {},
                    onRouteLaunch = { launches++ }, onToggleSavedRoute = { saves++ },
                )
            }
            }
        }
        composeRule.onNodeWithText("출발역과 도착역을 선택하고 경로를 저장해 보세요.").performScrollTo().assertExists()
        composeRule.onNodeWithContentDescription("강남 → 서울역 경로 저장").performScrollTo().performClick()
        composeRule.runOnIdle { assertTrue(saves == 1 && launches == 0) }
        composeRule.onNodeWithContentDescription("강남에서 서울역 도착 정보 바로 실행").performScrollTo().performClick()
        composeRule.runOnIdle { assertTrue(launches == 1) }
    }
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
