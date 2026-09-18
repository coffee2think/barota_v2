package com.gilbit.barota.data.remote

import kotlinx.serialization.Serializable

@Serializable
data class RealtimeArrivalResponse(
    val status: Int? = null,
    val code: String? = null,
    val message: String? = null,
    val errorMessage: ApiResult? = null,
    val realtimeArrivalList: List<RealtimeArrivalDto> = emptyList(),
)

@Serializable
data class ApiResult(
    val code: String = "",
    val message: String = "",
)

@Serializable
data class RealtimeArrivalDto(
    val rowNum: Int? = null,
    val ordkey: String = "",
    val subwayId: String = "",
    val updnLine: String = "",
    val trainLineNm: String = "",
    val statnNm: String = "",
    val btrainNo: String = "",
    val bstatnNm: String = "",
    val barvlDt: String = "",
    val btrainSttus: String = "",
    val recptnDt: String = "",
    val arvlMsg2: String = "",
    val arvlMsg3: String = "",
    val arvlCd: String = "",
    val lstcarAt: String = "0",
)
