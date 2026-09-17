package com.gilbit.barota.data.remote

import okhttp3.HttpUrl
import retrofit2.http.GET
import retrofit2.http.Url

interface TimetableApi {
    @GET
    suspend fun getTrainSchedule(@Url url: HttpUrl): TrainScheduleResponse
}
