package com.catan.model

import kotlinx.serialization.Serializable

@Serializable
data class HexCoord(val q: Int, val r: Int)

@Serializable
enum class VertexDirection { N, S }

@Serializable
data class VertexCoord(val q: Int, val r: Int, val dir: VertexDirection)

@Serializable
enum class EdgeDirection { NE, E, SE }

@Serializable
data class EdgeCoord(val q: Int, val r: Int, val dir: EdgeDirection)

@Serializable
enum class ResourceType { BRICK, LUMBER, ORE, GRAIN, WOOL }

@Serializable
enum class TileType {
    HILLS, FOREST, MOUNTAINS, FIELDS, PASTURE, DESERT;

    fun resource(): ResourceType? = when (this) {
        HILLS -> ResourceType.BRICK
        FOREST -> ResourceType.LUMBER
        MOUNTAINS -> ResourceType.ORE
        FIELDS -> ResourceType.GRAIN
        PASTURE -> ResourceType.WOOL
        DESERT -> null
    }
}

@Serializable
data class HexTile(
    val coord: HexCoord,
    val tileType: TileType,
    val diceNumber: Int? = null,
    val hasRobber: Boolean = false
)

@Serializable
enum class PortType {
    GENERIC_3_1, BRICK_2_1, LUMBER_2_1, ORE_2_1, GRAIN_2_1, WOOL_2_1
}

@Serializable
data class Port(
    val vertices: Pair<VertexCoord, VertexCoord>,
    val portType: PortType
)
