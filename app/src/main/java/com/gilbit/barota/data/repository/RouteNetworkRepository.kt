package com.gilbit.barota.data.repository

import android.content.Context
import com.gilbit.barota.data.model.RouteNetwork
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.json.Json

interface RouteNetworkRepository {
    fun getRouteNetwork(): RouteNetwork
}

@Singleton
class AssetRouteNetworkRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val json: Json,
) : RouteNetworkRepository {
    private val network by lazy {
        val content = context.assets.open(ROUTE_NETWORK_FILE).bufferedReader().use { it.readText() }
        json.decodeFromString<RouteNetwork>(content)
    }

    override fun getRouteNetwork(): RouteNetwork = network

    companion object {
        const val ROUTE_NETWORK_FILE = "route_network.json"
    }
}
