package com.catan.ai.heuristic

import com.catan.game.HexUtils
import com.catan.model.*

object VertexEvaluator {

    private val EARLY_GAME_RESOURCE_WEIGHTS = mapOf(
        ResourceType.BRICK to 1.2,
        ResourceType.LUMBER to 1.2,
        ResourceType.ORE to 0.8,
        ResourceType.GRAIN to 1.0,
        ResourceType.WOOL to 0.9
    )

    private val LATE_GAME_RESOURCE_WEIGHTS = mapOf(
        ResourceType.BRICK to 0.7,
        ResourceType.LUMBER to 0.7,
        ResourceType.ORE to 1.4,
        ResourceType.GRAIN to 1.3,
        ResourceType.WOOL to 0.8
    )

    private const val DIVERSITY_BONUS = 1.5
    private const val PORT_BONUS_SPECIFIC = 1.5
    private const val PORT_BONUS_GENERIC = 0.8
    private const val ROBBER_PENALTY = 2.0

    fun score(state: GameState, vertex: VertexCoord, playerId: String): Double {
        val tileMap = state.tiles.associateBy { it.coord }
        val adjacentHexes = HexUtils.hexesOfVertex(vertex)
        val isLateGame = isLateGame(state, playerId)
        val weights = if (isLateGame) LATE_GAME_RESOURCE_WEIGHTS else EARLY_GAME_RESOURCE_WEIGHTS

        var score = 0.0
        val uniqueResources = mutableSetOf<ResourceType>()

        for (hexCoord in adjacentHexes) {
            val tile = tileMap[hexCoord] ?: continue
            val resource = tile.tileType.resource() ?: continue
            val diceNumber = tile.diceNumber ?: continue

            val probability = diceProbability(diceNumber)
            val resourceWeight = weights[resource] ?: 1.0

            score += probability * resourceWeight

            uniqueResources.add(resource)

            if (tile.hasRobber) {
                score -= ROBBER_PENALTY
            }
        }

        // Diversity bonus
        score += DIVERSITY_BONUS * (uniqueResources.size - 1).coerceAtLeast(0)

        // Port bonus
        score += portBonus(state, vertex)

        return score
    }

    fun scoreCityUpgrade(state: GameState, vertex: VertexCoord, playerId: String): Double {
        val tileMap = state.tiles.associateBy { it.coord }
        val adjacentHexes = HexUtils.hexesOfVertex(vertex)

        var productionValue = 0.0
        for (hexCoord in adjacentHexes) {
            val tile = tileMap[hexCoord] ?: continue
            val resource = tile.tileType.resource() ?: continue
            val diceNumber = tile.diceNumber ?: continue
            productionValue += diceProbability(diceNumber)
            if (tile.hasRobber) productionValue -= 1.0
        }

        // City upgrade gives +1 resource per production hit, plus 1 VP
        return productionValue + 3.0 // 3.0 for the VP value
    }

    private fun portBonus(state: GameState, vertex: VertexCoord): Double {
        for (port in state.ports) {
            val portVertices = setOf(port.vertices.first, port.vertices.second)
            if (vertex in portVertices) {
                return when (port.portType) {
                    PortType.GENERIC_3_1 -> PORT_BONUS_GENERIC
                    else -> PORT_BONUS_SPECIFIC
                }
            }
        }
        return 0.0
    }

    private fun isLateGame(state: GameState, playerId: String): Boolean {
        val player = state.playerById(playerId) ?: return false
        val cityCount = state.buildings.count { it.playerId == playerId && it.type == BuildingType.CITY }
        return cityCount >= 2 || player.victoryPoints >= 6
    }

    fun diceProbability(diceNumber: Int): Double {
        return (6.0 - kotlin.math.abs(7.0 - diceNumber)) / 36.0 * 10.0
        // Scaled: 2/12→0.28, 6/8→1.39, etc. Multiply by 10 for nicer numbers
    }
}
