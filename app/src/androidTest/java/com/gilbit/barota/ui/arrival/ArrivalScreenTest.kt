package com.gilbit.barota.ui.arrival

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.gilbit.barota.data.model.DestinationStopStatus
import com.gilbit.barota.data.model.DestinationStopDiagnostic
import com.gilbit.barota.data.model.DestinationStopReason
import com.gilbit.barota.data.model.TrainArrival
import com.gilbit.barota.data.model.RouteLine
import com.gilbit.barota.data.model.RouteNetwork
import com.gilbit.barota.data.model.RouteService
import com.gilbit.barota.data.model.ServiceDirections
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
        composeRule.onAllNodesWithTag("arrival-card-green").assertCountEquals(1)
        composeRule.onAllNodesWithTag("arrival-card-red-flashing").assertCountEquals(1)
    }

    @Test
    fun trainCardDoesNotExposeInternalDiagnosticText() {
        composeRule.setContent {
            SubwayBarotaTheme(dynamicColor = false) {
                ArrivalScreen(
                    originName = "독산",
                    originLines = listOf("1호선"),
                    destinationName = "종로3가",
                    destinationLines = listOf("1호선"),
                    uiState = ArrivalUiState(
                        arrivals = listOf(
                            arrival("unknown", 120, DestinationStopStatus.UNKNOWN).copy(
                                line = "1호선",
                                direction = "상행",
                                terminalStation = "광운대",
                                destinationStopDiagnostic = DestinationStopDiagnostic(
                                    reason = DestinationStopReason.TIMETABLE_TRAIN_NUMBER_NOT_FOUND,
                                    originScheduleMatchCount = 0,
                                    attemptedTimetableTrainNumbers = listOf("K472", "S472"),
                                ),
                            ),
                        ),
                    ),
                    onBack = {},
                    onRefresh = {},
                )
            }
        }

        composeRule.onNodeWithText("노선 진단", substring = true).assertDoesNotExist()
        composeRule.onNodeWithText("실제 판정", substring = true).assertDoesNotExist()
        composeRule.onNodeWithText("시간표 매칭", substring = true).assertDoesNotExist()
        composeRule.onNodeWithText("시간표 열차번호", substring = true).assertDoesNotExist()
        composeRule.onNodeWithText("도착 데이터", substring = true).assertDoesNotExist()
    }

    @Test
    fun doksanRouteShowsUpDirectionAndDirectionSpecificEmptyResult() {
        val resolution = RouteDirectionResolver(
            RouteNetwork(
                version = 1,
                lines = listOf(
                    RouteLine(
                        line = "1호선",
                        subwayId = "1001",
                        services = listOf(
                            RouteService(
                                id = "main",
                                directions = ServiceDirections("하행", "상행"),
                                edges = listOf(listOf("종로3가", "독산")),
                            ),
                        ),
                    ),
                ),
            ),
        ).resolve(
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
