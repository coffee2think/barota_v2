package com.gilbit.barota.ui.arrival

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.gilbit.barota.data.model.DestinationStopStatus
import com.gilbit.barota.data.model.TrainArrival
import com.gilbit.barota.ui.theme.SubwayBarotaTheme
import com.gilbit.barota.domain.RouteDirectionResolver
import com.gilbit.barota.domain.RouteDirectionResult
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class ArrivalScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun stopAndNonStopButtonsChangeArrivalCardInPlace() {
        composeRule.setContent {
            SubwayBarotaTheme(dynamicColor = false) {
                ArrivalScreen(
                    originName = "강남",
                    originLines = listOf("2호선"),
                    destinationName = "서울역",
                    destinationLines = listOf("1호선", "4호선"),
                    uiState = ArrivalUiState(arrivals = emptyList()),
                    onBack = {},
                    onRefresh = {},
                )
            }
        }

        composeRule.onNodeWithText("정차 · 초록 카드 테스트").performScrollTo().performClick()
        composeRule.onNodeWithText("테스트용 열차 카드").assertExists()
        composeRule.onNodeWithTag("arrival-screen-green").assertExists()
        composeRule.onNodeWithTag("arrival-card-green").assertExists()
        composeRule.onNodeWithText("😊 목적지 정차 · 타도 돼요").assertExists()

        composeRule.onNodeWithText("미정차 · 빨강 점멸 테스트").performScrollTo().performClick()
        composeRule.onNodeWithTag("arrival-screen-red-flashing").assertExists()
        composeRule.onNodeWithTag("arrival-card-red-flashing").assertExists()
        composeRule.onNodeWithText("😠 목적지 미정차 · 타면 안 돼요").assertExists()

        composeRule.onNodeWithText("판정 표시 해제").performScrollTo().performClick()
        composeRule.onNodeWithTag("arrival-screen-default").assertExists()
        composeRule.onNodeWithTag("arrival-card-unknown").assertExists()
    }

    @Test
    fun screenUsesDestinationStatusOfFirstFinalListItemWithoutReordering() {
        composeRule.setContent {
            SubwayBarotaTheme(dynamicColor = false) {
                ArrivalScreen(
                    originName = "강남",
                    originLines = listOf("2호선"),
                    destinationName = "서울역",
                    destinationLines = listOf("1호선", "4호선"),
                    uiState = ArrivalUiState(
                        arrivals = listOf(
                            arrival("far", 300, DestinationStopStatus.STOPS),
                            arrival("nearest", 60, DestinationStopStatus.DOES_NOT_STOP),
                        ),
                    ),
                    onBack = {},
                    onRefresh = {},
                )
            }
        }

        composeRule.onNodeWithTag("arrival-screen-green").assertExists()
    }

    @Test
    fun doksanRouteShowsUpDirectionAndDirectionSpecificEmptyResult() {
        val resolution = RouteDirectionResolver().resolve(
            "독산", "종로3가", listOf("1호선"), listOf("1호선", "3호선", "5호선"),
        )
        composeRule.setContent {
            SubwayBarotaTheme(dynamicColor = false) {
                ArrivalScreen(
                    originName = "독산", originLines = listOf("1호선"),
                    destinationName = "종로3가", destinationLines = listOf("1호선", "3호선", "5호선"),
                    uiState = ArrivalUiState(routeDirection = resolution),
                    onBack = {}, onRefresh = {},
                )
            }
        }
        composeRule.onNodeWithText("1호선 · 상행 열차만 표시합니다.").assertExists()
        composeRule.onNodeWithText("현재 1호선 상행 도착정보가 없습니다.").performScrollTo().assertExists()
    }

    @Test
    fun lineSelectionIsExplicitAndDoesNotShowNormalEmptyResult() {
        var selected: String? = null
        composeRule.setContent {
            SubwayBarotaTheme(dynamicColor = false) {
                ArrivalScreen(
                    originName = "시청", originLines = listOf("1호선", "2호선"),
                    destinationName = "신도림", destinationLines = listOf("1호선", "2호선"),
                    uiState = ArrivalUiState(
                        routeDirection = RouteDirectionResult.LineSelectionRequired(listOf("1호선", "2호선")),
                        commonLines = listOf("1호선", "2호선"),
                    ),
                    onBack = {}, onRefresh = {}, onSelectLine = { selected = it },
                )
            }
        }
        composeRule.onNodeWithText("조회할 공통 노선을 선택해 주세요.").assertExists()
        composeRule.onNodeWithText("현재 시청 도착정보가 없습니다.").assertDoesNotExist()
        composeRule.onNodeWithTag("route-line-2호선").performClick()
        composeRule.runOnIdle { assertEquals("2호선", selected) }
    }

    @Test
    fun unsupportedRouteHasDistinctNoticeInsteadOfNormalEmptyResult() {
        val message = "방향을 확인할 수 없는 경로입니다."
        composeRule.setContent {
            SubwayBarotaTheme(dynamicColor = false) {
                ArrivalScreen(
                    originName = "독산", originLines = listOf("1호선"),
                    destinationName = "강남", destinationLines = listOf("2호선"),
                    uiState = ArrivalUiState(routeDirection = RouteDirectionResult.Unsupported(message)),
                    onBack = {}, onRefresh = {},
                )
            }
        }
        composeRule.onNodeWithText(message).assertExists()
        composeRule.onNodeWithText("현재 독산 도착정보가 없습니다.").assertDoesNotExist()
    }

    private fun arrival(
        id: String,
        seconds: Int,
        stopStatus: DestinationStopStatus,
    ) = TrainArrival(
        id = id,
        trainNumber = "1234",
        line = "2호선",
        direction = "내선",
        terminalStation = "성수",
        arrivalMessage = "${seconds}초 후",
        currentLocation = "역삼 출발",
        arrivalSeconds = seconds,
        trainType = "일반",
        isLastTrain = false,
        receivedAt = "2026-09-16 08:00:00",
        destinationStopStatus = stopStatus,
    )
}
