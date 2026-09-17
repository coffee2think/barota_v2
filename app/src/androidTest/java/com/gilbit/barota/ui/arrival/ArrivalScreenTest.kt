package com.gilbit.barota.ui.arrival

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.gilbit.barota.data.model.DestinationStopStatus
import com.gilbit.barota.data.model.TrainArrival
import com.gilbit.barota.ui.theme.SubwayBarotaTheme
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
    fun screenUsesDestinationStatusOfNearestArrival() {
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

        composeRule.onNodeWithTag("arrival-screen-red-flashing").assertExists()
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
