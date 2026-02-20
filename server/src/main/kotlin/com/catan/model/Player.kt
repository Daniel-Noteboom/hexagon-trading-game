package com.catan.model

import kotlinx.serialization.Serializable

@Serializable
enum class PlayerColor { RED, BLUE, WHITE, ORANGE }

@Serializable
enum class DevelopmentCardType {
    KNIGHT, VICTORY_POINT, ROAD_BUILDING, YEAR_OF_PLENTY, MONOPOLY
}

@Serializable
enum class AiDifficulty { EASY, MEDIUM, HARD }

@Serializable
data class Player(
    val id: String,
    val displayName: String,
    val color: PlayerColor,
    val resources: MutableMap<ResourceType, Int> = mutableMapOf(
        ResourceType.BRICK to 0,
        ResourceType.LUMBER to 0,
        ResourceType.ORE to 0,
        ResourceType.GRAIN to 0,
        ResourceType.WOOL to 0
    ),
    val devCards: MutableList<DevelopmentCardType> = mutableListOf(),
    val newDevCards: MutableList<DevelopmentCardType> = mutableListOf(),
    var knightsPlayed: Int = 0,
    var victoryPoints: Int = 0,
    var hasPlayedDevCardThisTurn: Boolean = false,
    val isAi: Boolean = false,
    val aiDifficulty: AiDifficulty? = null
) {
    fun totalResourceCount(): Int = resources.values.sum()

    fun hasResources(required: Map<ResourceType, Int>): Boolean =
        required.all { (type, amount) -> (resources[type] ?: 0) >= amount }

    fun deductResources(required: Map<ResourceType, Int>) {
        required.forEach { (type, amount) ->
            resources[type] = (resources[type] ?: 0) - amount
        }
    }

    fun addResources(gained: Map<ResourceType, Int>) {
        gained.forEach { (type, amount) ->
            resources[type] = (resources[type] ?: 0) + amount
        }
    }
}
