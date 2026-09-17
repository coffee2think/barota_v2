package com.gilbit.barota.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.gilbit.barota.ui.arrival.ArrivalRoute
import com.gilbit.barota.ui.selection.StationSelectionRoute
import kotlinx.serialization.Serializable

@Serializable
private data object StationSelection

@Serializable
private data class SelectionSummary(
    val originName: String,
    val originLines: String,
    val destinationName: String,
    val destinationLines: String,
)

@Composable
fun BarotaNavHost() {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = StationSelection) {
        composable<StationSelection> {
            StationSelectionRoute(
                onSearch = { origin, destination ->
                    navController.navigate(
                        SelectionSummary(
                            originName = origin.name,
                            originLines = origin.lines.joinToString(","),
                            destinationName = destination.name,
                            destinationLines = destination.lines.joinToString(","),
                        ),
                    )
                },
            )
        }
        composable<SelectionSummary> { entry ->
            val route = entry.toRoute<SelectionSummary>()
            ArrivalRoute(
                originName = route.originName,
                originLines = route.originLines.split(","),
                destinationName = route.destinationName,
                destinationLines = route.destinationLines.split(","),
                onBack = navController::navigateUp,
            )
        }
    }
}
