package com.catan.ai.heuristic

import com.catan.model.*

object DevCardEvaluator {

    private const val LARGEST_ARMY_BONUS = 5.0
    private const val KNIGHT_BASE = 1.0
    private const val ROAD_BUILDING_BASE = 2.0
    private const val MONOPOLY_BASE = 2.0
    private const val YEAR_OF_PLENTY_BASE = 1.5

    fun scoreBuy(state: GameState, playerId: String): Double {
        val player = state.playerById(playerId) ?: return 0.0
        if (state.devCardDeck.isEmpty()) return 0.0

        var score = 1.0 // Base value of a dev card

        // Bonus if close to largest army
        val knightsNeeded = knightsToLargestArmy(state, playerId)
        if (knightsNeeded <= 2) {
            score += (3.0 - knightsNeeded) * 1.5
        }

        // Slight penalty if resources could be used for a city instead
        if (player.hasResources(mapOf(ResourceType.GRAIN to 2, ResourceType.ORE to 3))) {
            score -= 1.0
        }

        return score
    }

    fun scorePlayKnight(state: GameState, playerId: String): Double {
        val player = state.playerById(playerId) ?: return 0.0
        var score = KNIGHT_BASE

        // Big bonus if this triggers largest army
        val knightsAfter = player.knightsPlayed + 1
        val currentHolder = state.largestArmyHolder
        if (currentHolder == null && knightsAfter >= 3) {
            score += LARGEST_ARMY_BONUS
        } else if (currentHolder != null && currentHolder != playerId) {
            val holderKnights = state.playerById(currentHolder)?.knightsPlayed ?: 0
            if (knightsAfter > holderKnights) {
                score += LARGEST_ARMY_BONUS
            }
        }

        // Bonus if robber is on AI's hex
        val robberHex = state.robberLocation
        val robberVertices = com.catan.game.HexUtils.verticesOfHex(robberHex)
        val hasOwnBuildingUnderRobber = robberVertices.any { v ->
            state.buildingAt(v)?.playerId == playerId
        }
        if (hasOwnBuildingUnderRobber) {
            score += 2.0
        }

        return score
    }

    fun scorePlayRoadBuilding(state: GameState, playerId: String): Double {
        var score = ROAD_BUILDING_BASE

        // Bonus if close to longest road
        val currentLength = com.catan.game.LongestRoadCalculator.calculate(
            playerId, state.roads, state.buildings
        )
        if (currentLength >= 3 && state.longestRoadHolder != playerId) {
            score += 3.0 // Close to getting longest road with 2 free roads
        }

        // Save resources (brick + lumber for 2 roads = 2 brick + 2 lumber)
        score += 1.0

        return score
    }

    fun scorePlayMonopoly(state: GameState, playerId: String, resource: ResourceType): Double {
        var score = MONOPOLY_BASE

        // Estimate opponent holdings based on their production hexes and building count
        val estimatedSteal = estimateResourceFromOpponents(state, playerId, resource)
        score += estimatedSteal * 0.8

        return score
    }

    fun scorePlayYearOfPlenty(
        state: GameState,
        playerId: String,
        resource1: ResourceType,
        resource2: ResourceType
    ): Double {
        val player = state.playerById(playerId) ?: return 0.0
        var score = YEAR_OF_PLENTY_BASE

        // Bonus if the resources complete a build
        val simResources = player.resources.toMutableMap()
        simResources[resource1] = (simResources[resource1] ?: 0) + 1
        simResources[resource2] = (simResources[resource2] ?: 0) + 1

        val simPlayer = player.copy(resources = simResources)

        if (simPlayer.hasResources(mapOf(ResourceType.GRAIN to 2, ResourceType.ORE to 3))) {
            score += 3.0 // Can build a city
        } else if (simPlayer.hasResources(mapOf(
                ResourceType.BRICK to 1, ResourceType.LUMBER to 1,
                ResourceType.GRAIN to 1, ResourceType.WOOL to 1
            ))) {
            score += 2.0 // Can build a settlement
        }

        return score
    }

    private fun knightsToLargestArmy(state: GameState, playerId: String): Int {
        val player = state.playerById(playerId) ?: return 99
        val currentHolder = state.largestArmyHolder
        val target = if (currentHolder != null && currentHolder != playerId) {
            val holderKnights = state.playerById(currentHolder)?.knightsPlayed ?: 0
            holderKnights + 1
        } else if (currentHolder == null) {
            3
        } else {
            // Already holds it
            return 0
        }
        val knightsInHand = player.devCards.count { it == DevelopmentCardType.KNIGHT }
        val needed = target - player.knightsPlayed - knightsInHand
        return maxOf(0, needed)
    }

    private fun estimateResourceFromOpponents(
        state: GameState,
        playerId: String,
        resource: ResourceType
    ): Double {
        var total = 0.0
        for (opponent in state.players) {
            if (opponent.id == playerId) continue
            // Use actual resource count as an approximation
            total += (opponent.resources[resource] ?: 0).toDouble()
        }
        return total
    }
}
