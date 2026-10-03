package com.gilbit.barota.ui.selection

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material.icons.rounded.Train
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gilbit.barota.data.model.Station
import com.gilbit.barota.domain.RouteShortcut

@Composable
fun StationSelectionRoute(
    onSearch: (Station, Station) -> Unit,
    viewModel: StationSelectionViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    StationSelectionScreen(
        uiState = uiState,
        onOpenSelector = viewModel::openSelector,
        onCloseSelector = viewModel::closeSelector,
        onQueryChange = viewModel::updateQuery,
        onStationSelected = viewModel::selectStation,
        onSwap = viewModel::swapStations,
        onMessageShown = viewModel::clearMessage,
        onSearch = { viewModel.confirmSearch(onSearch) },
        onRouteLaunch = { viewModel.launchRoute(it, onSearch) },
        onToggleSavedRoute = viewModel::toggleSavedRoute,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StationSelectionScreen(
    uiState: StationSelectionUiState,
    onOpenSelector: (SelectionTarget) -> Unit,
    onCloseSelector: () -> Unit,
    onQueryChange: (String) -> Unit,
    onStationSelected: (Station) -> Unit,
    onSwap: () -> Unit,
    onMessageShown: () -> Unit,
    onSearch: () -> Unit,
    onRouteLaunch: (RouteShortcut) -> Unit = {},
    onToggleSavedRoute: (RouteShortcut) -> Unit = {},
) {
    val snackbarHostState = remember { androidx.compose.material3.SnackbarHostState() }

    LaunchedEffect(uiState.message) {
        uiState.message?.let {
            snackbarHostState.showSnackbar(it)
            onMessageShown()
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "지하철 바로타",
                        fontWeight = FontWeight.Bold,
                    )
                },
            )
        },
        snackbarHost = { androidx.compose.material3.SnackbarHost(snackbarHostState) },
        bottomBar = {
            Column(Modifier.navigationBarsPadding().padding(horizontal = 24.dp, vertical = 16.dp)) {
                Button(onClick = onSearch, enabled = uiState.canSearch,
                    modifier = Modifier.fillMaxWidth().height(56.dp), shape = RoundedCornerShape(18.dp)) {
                    Text("도착 정보 확인", style = MaterialTheme.typography.titleMedium)
                }
            }
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
        ) {
            Spacer(Modifier.height(20.dp))
            Text(
                text = "어디로 떠나세요?",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "출발역과 도착역을 선택하면\n필요한 지하철 정보를 바로 확인할 수 있어요.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(32.dp))

            if (uiState.isLoading) {
                Box(Modifier.fillMaxWidth().height(220.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                StationCard(
                    label = "출발",
                    station = uiState.origin,
                    placeholder = "출발역을 선택하세요",
                    onClick = { onOpenSelector(SelectionTarget.ORIGIN) },
                )

                Box(Modifier.fillMaxWidth().height(60.dp), contentAlignment = Alignment.Center) {
                    FilledIconButton(
                        onClick = onSwap,
                        enabled = uiState.origin != null || uiState.destination != null,
                        modifier = Modifier.semantics { contentDescription = "출발역과 도착역 맞바꾸기" },
                    ) {
                        Icon(Icons.Rounded.SwapVert, contentDescription = null)
                    }
                }

                StationCard(
                    label = "도착",
                    station = uiState.destination,
                    placeholder = "도착역을 선택하세요",
                    onClick = { onOpenSelector(SelectionTarget.DESTINATION) },
                )
            }

            val selectedRoute = uiState.origin?.let { origin ->
                uiState.destination?.let { destination -> RouteShortcut(origin, destination) }
            }
            TextButton(
                onClick = { selectedRoute?.let(onToggleSavedRoute) },
                enabled = selectedRoute != null && selectedRoute.origin.id != selectedRoute.destination.id && !uiState.isSavingRoute,
                modifier = Modifier.align(Alignment.End),
            ) { Text(if (selectedRoute in uiState.savedRoutes) "저장 해제" else "경로 저장") }
            Text("저장한 경로", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            if (uiState.savedRoutes.isEmpty()) {
                Text("출발역과 도착역을 선택하고 경로를 저장해 보세요.",
                    modifier = Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            uiState.savedRoutes.forEach { route ->
                RouteShortcutRow(route, true, !uiState.isSubmitting, !uiState.isSavingRoute,
                    { onRouteLaunch(route) }, { onToggleSavedRoute(route) })
            }
            if (uiState.recommendedRoutes.isNotEmpty()) {
                Spacer(Modifier.height(20.dp))
                Text("자주 이용한 경로", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                uiState.recommendedRoutes.forEach { route ->
                    RouteShortcutRow(route, false, !uiState.isSubmitting, !uiState.isSavingRoute,
                        { onRouteLaunch(route) }, { onToggleSavedRoute(route) })
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (uiState.selectionTarget != null) {
        StationPickerSheet(
            target = uiState.selectionTarget,
            query = uiState.query,
            stations = uiState.filteredStations,
            frequentStations = uiState.frequentStations,
            onQueryChange = onQueryChange,
            onStationSelected = onStationSelected,
            onDismiss = onCloseSelector,
        )
    }
}

@Composable
private fun RouteShortcutRow(
    route: RouteShortcut, saved: Boolean, canLaunch: Boolean, canSave: Boolean,
    onLaunch: () -> Unit, onToggleSaved: () -> Unit,
) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).clip(RoundedCornerShape(12.dp))
            .clickable(enabled = canLaunch, onClick = onLaunch)
            .semantics { contentDescription = "${route.origin.name}에서 ${route.destination.name} 도착 정보 바로 실행" }
            .padding(vertical = 16.dp, horizontal = 8.dp)) {
            Text("${route.origin.name} → ${route.destination.name}", fontWeight = FontWeight.SemiBold)
        }
        TextButton(onClick = onToggleSaved, enabled = canSave,
            modifier = Modifier.semantics { contentDescription = "${route.origin.name} → ${route.destination.name} ${if (saved) "저장 해제" else "경로 저장"}" }) {
            Text(if (saved) "저장 해제" else "저장")
        }
    }
    HorizontalDivider()
}

@Composable
private fun StationCard(
    label: String,
    station: Station?,
    placeholder: String,
    onClick: () -> Unit,
) {
    OutlinedCard(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Rounded.Train,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
            Column(Modifier.weight(1f)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = station?.name ?: placeholder,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = if (station == null) FontWeight.Normal else FontWeight.Bold,
                    color = if (station == null) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                )
                station?.let {
                    Spacer(Modifier.height(8.dp))
                    LineBadges(it.lines)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StationPickerSheet(
    target: SelectionTarget,
    query: String,
    stations: List<Station>,
    frequentStations: List<Station>,
    onQueryChange: (String) -> Unit,
    onStationSelected: (Station) -> Unit,
    onDismiss: () -> Unit,
) {
    val listState = rememberLazyListState()
    LaunchedEffect(query.trim()) { listState.scrollToItem(0) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.88f)
                .navigationBarsPadding(),
        ) {
            Text(
                text = if (target == SelectionTarget.ORIGIN) "출발역 찾기" else "도착역 찾기",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 24.dp),
            )
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                placeholder = { Text("역 이름 또는 호선 검색") },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { onQueryChange("") }) {
                            Icon(Icons.Rounded.Close, contentDescription = "검색어 지우기")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(18.dp),
            )
            Spacer(Modifier.height(12.dp))

            if (stations.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("검색 결과가 없어요", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(bottom = 24.dp),
                ) {
                    if (query.isBlank()) {
                        if (frequentStations.isNotEmpty()) {
                            item(key = "frequent-header") { StationSectionTitle("자주 찾는 역") }
                            items(frequentStations, key = { "frequent-${it.id}" }) { station ->
                                StationRow(station = station, onClick = { onStationSelected(station) })
                                HorizontalDivider(modifier = Modifier.padding(start = 76.dp))
                            }
                        }
                        item(key = "all-header") { StationSectionTitle("전체 역") }
                    }
                    items(stations, key = { "all-${it.id}" }) { station ->
                        StationRow(station = station, onClick = { onStationSelected(station) })
                        HorizontalDivider(modifier = Modifier.padding(start = 76.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun StationSectionTitle(title: String) {
    Text(
        title,
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontWeight = FontWeight.Bold,
    )
}

@Composable
private fun StationRow(station: Station, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(Icons.Rounded.Train, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Column(Modifier.weight(1f)) {
            Text(
                text = station.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(6.dp))
            LineBadges(station.lines)
        }
    }
}

@Composable
fun LineBadges(lines: List<String>) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        lines.take(4).forEach { line ->
            Text(
                text = line,
                style = MaterialTheme.typography.labelSmall,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(lineColor(line))
                    .padding(horizontal = 9.dp, vertical = 4.dp),
            )
        }
    }
}

internal fun lineColor(line: String): Color = when (line) {
    "1호선" -> Color(0xFF0052A4)
    "2호선" -> Color(0xFF00A84D)
    "3호선" -> Color(0xFFEF7C1C)
    "4호선" -> Color(0xFF00A5DE)
    "5호선" -> Color(0xFF996CAC)
    "6호선" -> Color(0xFFCD7C2F)
    "7호선" -> Color(0xFF747F00)
    "8호선" -> Color(0xFFE6186C)
    "9호선" -> Color(0xFFBDB092)
    "경의중앙선" -> Color(0xFF77C4A3)
    "수인분당선" -> Color(0xFFF5A200)
    "신분당선" -> Color(0xFFD4003B)
    "공항철도" -> Color(0xFF0090D2)
    else -> Color(0xFF607D8B)
}
