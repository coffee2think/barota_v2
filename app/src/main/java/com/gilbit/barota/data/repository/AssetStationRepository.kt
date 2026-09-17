package com.gilbit.barota.data.repository

import android.content.Context
import com.gilbit.barota.data.model.Station
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.serialization.json.Json
import javax.inject.Inject

class AssetStationRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val json: Json,
) : StationRepository {
    override suspend fun getStations(): List<Station> {
        val content = context.assets.open(STATIONS_FILE).bufferedReader().use { it.readText() }
        return json.decodeFromString<List<Station>>(content)
            .sortedWith(compareBy(Station::name, { it.lines.firstOrNull().orEmpty() }))
    }

    private companion object {
        const val STATIONS_FILE = "stations.json"
    }
}
