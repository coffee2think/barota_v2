package com.gilbit.barota.data.repository

import com.gilbit.barota.data.model.Station

interface StationRepository {
    suspend fun getStations(): List<Station>
}
