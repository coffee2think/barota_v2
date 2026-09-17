package com.gilbit.barota.data.model

import kotlinx.serialization.Serializable

@Serializable
data class Station(
    val id: String,
    val name: String,
    val lines: List<String>,
)
