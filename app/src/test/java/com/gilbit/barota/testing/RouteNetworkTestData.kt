package com.gilbit.barota.testing

import com.gilbit.barota.data.model.RouteNetwork
import com.gilbit.barota.domain.RouteDirectionResolver
import java.io.File
import kotlinx.serialization.json.Json

object RouteNetworkTestData {
    val network: RouteNetwork by lazy {
        Json.decodeFromString<RouteNetwork>(
            File("src/main/assets/route_network.json").readText(Charsets.UTF_8),
        )
    }

    fun resolver(): RouteDirectionResolver = RouteDirectionResolver(network)
}
