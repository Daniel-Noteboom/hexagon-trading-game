package com.catan.model

import kotlinx.serialization.Serializable

@Serializable
enum class BuildingType { SETTLEMENT, CITY }

@Serializable
data class Building(
    val vertex: VertexCoord,
    val playerId: String,
    val type: BuildingType
)

@Serializable
data class Road(
    val edge: EdgeCoord,
    val playerId: String
)
