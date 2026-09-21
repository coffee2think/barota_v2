package com.gilbit.barota.ui.arrival

import androidx.compose.animation.animateColor
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Train
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gilbit.barota.data.model.TrainArrival
import com.gilbit.barota.data.model.DestinationStopStatus
import com.gilbit.barota.ui.selection.LineBadges
import com.gilbit.barota.domain.RouteDirectionResult
import com.gilbit.barota.domain.effectiveArrivalSeconds

@Composable
fun ArrivalRoute(
    originName: String,
    originLines: List<String>,
    destinationName: String,
    destinationLines: List<String>,
    onBack: () -> Unit,
    viewModel: ArrivalViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(originName, destinationName, originLines, destinationLines) {
        viewModel.load(originName, destinationName, originLines, destinationLines)
    }

    ArrivalScreen(
        originName = originName,
        originLines = originLines,
        destinationName = destinationName,
        destinationLines = destinationLines,
        uiState = uiState,
        onBack = onBack,
        onRefresh = viewModel::refresh,
        onSelectLine = viewModel::selectLine,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArrivalScreen(
    originName: String,
    originLines: List<String>,
    destinationName: String,
    destinationLines: List<String>,
    uiState: ArrivalUiState,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onSelectLine: (String) -> Unit = {},
) {
    val firstArrival = uiState.arrivals.firstOrNull()
    val screenStopStatus = firstArrival?.destinationStopStatus ?: DestinationStopStatus.UNKNOWN
    val warningPulse = rememberInfiniteTransition(label = "미정차 경고 점멸")
    val flashingRed by warningPulse.animateColor(
        initialValue = Color(0xFFFFDAD6),
        targetValue = Color(0xFFFF8A80),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 700),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "미정차 빨간 배경",
    )
    val screenBackgroundColor = when (screenStopStatus) {
        DestinationStopStatus.STOPS -> Color(0xFFDDF6E8)
        DestinationStopStatus.DOES_NOT_STOP -> flashingRed
        DestinationStopStatus.UNKNOWN -> MaterialTheme.colorScheme.background
    }

    Scaffold(
        modifier = Modifier.testTag(
            when (screenStopStatus) {
                DestinationStopStatus.STOPS -> "arrival-screen-green"
                DestinationStopStatus.DOES_NOT_STOP -> "arrival-screen-red-flashing"
                DestinationStopStatus.UNKNOWN -> "arrival-screen-default"
            },
        ),
        containerColor = screenBackgroundColor,
        topBar = {
            TopAppBar(
                title = { Text("실시간 도착정보", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = screenBackgroundColor,
                    scrolledContainerColor = screenBackgroundColor,
                ),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "뒤로가기")
                    }
                },
                actions = {
                    IconButton(onClick = onRefresh, enabled = !uiState.isLoading) {
                        Icon(Icons.Rounded.Refresh, contentDescription = "도착정보 새로고침")
                    }
                },
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                RouteHeader(
                    originName = originName,
                    originLines = originLines,
                    destinationName = destinationName,
                    destinationLines = destinationLines,
                    updatedAt = uiState.updatedAt,
                )
            }

            item {
                when (val direction = uiState.routeDirection) {
                    is RouteDirectionResult.Resolved -> Text(
                        "${direction.route.line} · ${direction.route.direction.label} 열차만 표시합니다.",
                        modifier = Modifier.testTag("route-direction"),
                    )
                    is RouteDirectionResult.LineSelectionRequired -> Text("조회할 공통 노선을 선택해 주세요.")
                    is RouteDirectionResult.Unsupported -> Text(direction.message)
                    is RouteDirectionResult.Unknown -> Text(direction.message)
                    null -> Unit
                }
            }

            if (uiState.commonLines.size > 1) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        uiState.commonLines.forEach { line ->
                            OutlinedButton(
                                onClick = { onSelectLine(line) },
                                enabled = line != uiState.selectedLine,
                                modifier = Modifier.testTag("route-line-$line"),
                            ) { Text(line) }
                        }
                    }
                }
            }

            if (uiState.arrivals.any { it.effectiveArrivalSeconds() == null }) {
                item {
                    Text("도착시간 미확인 열차는 뒤에 표시합니다. 미확인 열차 간 실제 진입 순서는 보장하지 않습니다.")
                }
            }

            when {
                uiState.routeDirection != null && uiState.routeDirection !is RouteDirectionResult.Resolved -> Unit
                uiState.isLoading && uiState.arrivals.isEmpty() -> item {
                    Box(Modifier.fillMaxWidth().height(240.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }

                uiState.errorMessage != null && uiState.arrivals.isEmpty() -> item {
                    MessageCard(uiState.errorMessage, onRefresh)
                }

                uiState.arrivals.isEmpty() -> item {
                    val route = uiState.routeDirection?.route
                    val target = route?.let { "${it.line} ${it.direction.label}" } ?: originName
                    MessageCard("현재 $target 도착정보가 없습니다.", onRefresh)
                }

                else -> {
                    item {
                        Text(
                            "${originName}으로 들어오는 열차",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                    items(uiState.arrivals, key = TrainArrival::id) { arrival ->
                        ArrivalCard(
                            arrival = arrival,
                            flashingRed = flashingRed,
                        )
                    }
                    if (uiState.errorMessage != null) {
                        item { MessageCard(uiState.errorMessage, onRefresh) }
                    }
                }
            }
        }
    }
}

@Composable
private fun RouteHeader(
    originName: String,
    originLines: List<String>,
    destinationName: String,
    destinationLines: List<String>,
    updatedAt: String?,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        shape = RoundedCornerShape(20.dp),
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("출발", style = MaterialTheme.typography.labelMedium)
                    Text(originName, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    LineBadges(originLines)
                }
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("도착", style = MaterialTheme.typography.labelMedium)
                    Text(destinationName, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    LineBadges(destinationLines)
                }
            }
            Text(
                updatedAt?.let { "데이터 수신 시각 $it" } ?: "서울시 실시간 데이터를 조회합니다.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}

@Composable
private fun ArrivalCard(
    arrival: TrainArrival,
    flashingRed: Color,
) {
    val stopStatus = arrival.destinationStopStatus
    val containerColor = when (stopStatus) {
        DestinationStopStatus.STOPS -> Color(0xFFDDF6E8)
        DestinationStopStatus.DOES_NOT_STOP -> flashingRed
        DestinationStopStatus.UNKNOWN -> MaterialTheme.colorScheme.surfaceContainer
    }
    val contentColor = when (stopStatus) {
        DestinationStopStatus.STOPS -> Color(0xFF075E38)
        DestinationStopStatus.DOES_NOT_STOP -> Color(0xFF681411)
        DestinationStopStatus.UNKNOWN -> MaterialTheme.colorScheme.onSurface
    }
    val cardTag = when (stopStatus) {
        DestinationStopStatus.STOPS -> "arrival-card-green"
        DestinationStopStatus.DOES_NOT_STOP -> "arrival-card-red-flashing"
        DestinationStopStatus.UNKNOWN -> "arrival-card-unknown"
    }

    Card(
        modifier = Modifier.fillMaxWidth().testTag(cardTag),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor, contentColor = contentColor),
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (arrival.effectiveArrivalSeconds() == null) {
                Text("도착시간 확인 중", style = MaterialTheme.typography.bodySmall)
            }
            if (stopStatus != DestinationStopStatus.UNKNOWN) {
                Text(
                    if (stopStatus == DestinationStopStatus.STOPS) {
                        "😊 목적지 정차 · 타도 돼요"
                    } else {
                        "😠 목적지 미정차 · 타면 안 돼요"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = contentColor,
                )
            } else {
                Text(
                    "목적지 정차 여부 확인 중",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    color = contentColor,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(Icons.Rounded.Train, contentDescription = null, tint = contentColor)
                LineBadges(listOf(arrival.line))
                Text(
                    arrival.direction,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        "열차번호",
                        style = MaterialTheme.typography.labelSmall,
                        color = contentColor,
                    )
                    Text(
                        arrival.trainNumber.ifBlank { "확인 불가" },
                        modifier = Modifier.testTag("arrival-train-number"),
                        style = MaterialTheme.typography.titleMedium,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = contentColor,
                    )
                }
            }
            Text(
                arrival.arrivalMessage,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = contentColor,
            )
            if (arrival.currentLocation.isNotBlank()) {
                Text(
                    "현재 위치 · ${arrival.currentLocation}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = contentColor,
                )
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (arrival.terminalStation.isNotBlank()) {
                    AssistChip(onClick = {}, label = { Text("행선지 ${arrival.terminalStation}행") })
                }
                if (arrival.trainType.isNotBlank()) {
                    AssistChip(onClick = {}, label = { Text("열차 종류 ${arrival.trainType}") })
                }
                if (arrival.isLastTrain) {
                    AssistChip(onClick = {}, label = { Text("막차") })
                }
            }
        }
    }
}

@Composable
private fun MessageCard(message: String, onRetry: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(message, color = MaterialTheme.colorScheme.onErrorContainer)
            Button(onClick = onRetry) { Text("다시 시도") }
        }
    }
}
