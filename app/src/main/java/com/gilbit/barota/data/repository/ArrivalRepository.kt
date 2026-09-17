package com.gilbit.barota.data.repository

import com.gilbit.barota.data.model.TrainArrival

interface ArrivalRepository {
    suspend fun getArrivals(stationName: String, destinationName: String): List<TrainArrival>
}

class MissingApiKeyException : IllegalStateException(
    "서울시 API 키가 설정되지 않았습니다. local.properties에 SEOUL_SUBWAY_API_KEY를 입력해 주세요.",
)

class SeoulApiException(message: String) : IllegalStateException(message)
