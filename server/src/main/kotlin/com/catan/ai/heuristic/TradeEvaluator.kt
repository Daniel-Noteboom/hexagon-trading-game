package com.catan.ai.heuristic

import com.catan.ai.ActionGenerator
import com.catan.ai.DifficultyConfig
import com.catan.model.*

object TradeEvaluator {

    private const val LEADER_HELP_PENALTY = 3.0

    fun scoreBankTrade(
        state: GameState,
        action: GameAction.BankTrade,
        playerId: String
    ): Double {
        val player = state.playerById(playerId) ?: return -10.0
        val ratio = action.givingAmount

        val needScore = resourceNeedScore(state, player, action.receiving)
        val surplusScore = resourceSurplusScore(player, action.giving)

        // Cost is giving away `ratio` of a resource; benefit is getting 1 needed resource
        return needScore - surplusScore * ratio * 0.3
    }

    fun scoreTradeResponse(
        state: GameState,
        trade: TradeOffer,
        playerId: String,
        config: DifficultyConfig
    ): Double {
        val player = state.playerById(playerId) ?: return -10.0

        var receiveValue = 0.0
        for ((resource, amount) in trade.offering) {
            receiveValue += resourceNeedScore(state, player, resource) * amount
        }

        var giveValue = 0.0
        for ((resource, amount) in trade.requesting) {
            giveValue += resourceValue(player, resource) * amount
        }

        var score = receiveValue - giveValue

        // Penalize helping the leader
        if (config.considerBlocking) {
            val offerer = state.playerById(trade.fromPlayerId)
            val maxVp = state.players.maxOf { it.victoryPoints }
            if (offerer != null && offerer.victoryPoints >= maxVp && state.players.size > 2) {
                score -= LEADER_HELP_PENALTY
            }
        }

        return score - config.tradeAcceptThreshold
    }

    fun scoreOfferTrade(
        state: GameState,
        action: GameAction.OfferTrade,
        playerId: String
    ): Double {
        val player = state.playerById(playerId) ?: return -10.0

        // Value of what we receive vs what we give
        var receiveValue = 0.0
        for ((resource, amount) in action.requesting) {
            receiveValue += resourceNeedScore(state, player, resource) * amount
        }

        var giveValue = 0.0
        for ((resource, amount) in action.offering) {
            giveValue += resourceValue(player, resource) * amount
        }

        // Slight penalty for trades (they take time, opponent might decline)
        return (receiveValue - giveValue) * 0.7 - 0.5
    }

    private fun resourceNeedScore(state: GameState, player: Player, resource: ResourceType): Double {
        val current = player.resources[resource] ?: 0

        // Check if this resource is needed for the most valuable build
        val cityNeed = mapOf(ResourceType.GRAIN to 2, ResourceType.ORE to 3)
        val settlementNeed = mapOf(
            ResourceType.BRICK to 1, ResourceType.LUMBER to 1,
            ResourceType.GRAIN to 1, ResourceType.WOOL to 1
        )
        val devCardNeed = mapOf(ResourceType.ORE to 1, ResourceType.GRAIN to 1, ResourceType.WOOL to 1)

        var maxNeed = 0.0

        // City: highest value build
        val cityShortfall = (cityNeed[resource] ?: 0) - current
        if (cityShortfall > 0) {
            val otherCityResources = cityNeed.entries
                .filter { it.key != resource }
                .all { player.hasResources(mapOf(it.key to it.value)) }
            if (otherCityResources) maxNeed = maxOf(maxNeed, 4.0)
            else maxNeed = maxOf(maxNeed, 2.0)
        }

        // Settlement
        val settlementShortfall = (settlementNeed[resource] ?: 0) - current
        if (settlementShortfall > 0) {
            val otherSettlementResources = settlementNeed.entries
                .filter { it.key != resource }
                .all { player.hasResources(mapOf(it.key to it.value)) }
            if (otherSettlementResources) maxNeed = maxOf(maxNeed, 3.0)
            else maxNeed = maxOf(maxNeed, 1.5)
        }

        // Dev card
        val devCardShortfall = (devCardNeed[resource] ?: 0) - current
        if (devCardShortfall > 0) {
            maxNeed = maxOf(maxNeed, 1.0)
        }

        // If we have none, it's more needed
        if (current == 0) maxNeed += 0.5

        return maxNeed
    }

    private fun resourceSurplusScore(player: Player, resource: ResourceType): Double {
        val count = player.resources[resource] ?: 0
        return when {
            count >= 5 -> 0.3 // Very surplus, cheap to give
            count >= 4 -> 0.5
            count >= 3 -> 0.8
            count >= 2 -> 1.5
            else -> 3.0 // Expensive to give up
        }
    }

    private fun resourceValue(player: Player, resource: ResourceType): Double {
        val count = player.resources[resource] ?: 0
        return when {
            count == 0 -> 4.0 // Can't afford to give what we don't have
            count == 1 -> 3.0 // Giving our last one is expensive
            count == 2 -> 2.0
            count == 3 -> 1.0
            else -> 0.5
        }
    }
}
