package com.gilbit.barota.data.remote

import retrofit2.http.GET
import retrofit2.http.Path

interface SeoulSubwayApi {
    @GET("{apiKey}/json/realtimeStationArrival/0/100/{stationName}")
    suspend fun getRealtimeArrivals(
        @Path("apiKey") apiKey: String,
        @Path("stationName") stationName: String,
    ): RealtimeArrivalResponse
}
