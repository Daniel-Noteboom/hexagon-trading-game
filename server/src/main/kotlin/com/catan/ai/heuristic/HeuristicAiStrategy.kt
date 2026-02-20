package com.catan.ai.heuristic

import com.catan.ai.ActionGenerator
import com.catan.ai.AiStrategy
import com.catan.ai.DifficultyConfig
import com.catan.model.*
import java.util.Random
import kotlin.math.absoluteValue

class HeuristicAiStrategy(
    private val config: DifficultyConfig,
    private val random: Random = Random()
) : AiStrategy {

    companion object {
        private const val END_TURN_BASE_SCORE = -0.5
        private const val STEAL_RICHEST_BONUS = 1.0
    }

    override fun chooseAction(state: GameState, playerId: String): GameAction {
        val legalActions = ActionGenerator.generate(state, playerId)
        if (legalActions.isEmpty()) {
            throw IllegalStateException("No legal actions available for player $playerId in phase=${state.phase}, turnPhase=${state.turnPhase}")
        }
        if (legalActions.size == 1) return legalActions.first()

        val scored = legalActions.map { action ->
            val score = scoreAction(state, playerId, action)
            val noise = if (config.randomnessFactor > 0) {
                random.nextGaussian() * config.randomnessFactor * (score.absoluteValue + 1.0)
            } else {
                0.0
            }
            action to (score + noise)
        }

        return scored.maxByOrNull { it.second }!!.first
    }

    private fun scoreAction(state: GameState, playerId: String, action: GameAction): Double {
        return when (action) {
            is GameAction.PlaceSettlement -> {
                when (state.phase) {
                    GamePhase.SETUP_FORWARD, GamePhase.SETUP_REVERSE ->
                        VertexEvaluator.score(state, action.vertex, playerId)
                    else ->
                        VertexEvaluator.score(state, action.vertex, playerId) + 5.0 // Bonus for main-phase settlement
                }
            }
            is GameAction.PlaceCity ->
                VertexEvaluator.scoreCityUpgrade(state, action.vertex, playerId) + 6.0

            is GameAction.PlaceRoad -> {
                when {
                    state.phase == GamePhase.SETUP_FORWARD || state.phase == GamePhase.SETUP_REVERSE ->
                        RoadEvaluator.score(state, action.edge, playerId, config)
                    state.roadBuildingRoadsLeft > 0 ->
                        RoadEvaluator.score(state, action.edge, playerId, config) + 1.0
                    else ->
                        RoadEvaluator.score(state, action.edge, playerId, config)
                }
            }

            is GameAction.MoveRobber ->
                RobberEvaluator.score(state, action.hex, playerId)

            is GameAction.StealResource -> {
                val target = state.playerById(action.targetPlayerId)
                val resourceCount = target?.totalResourceCount()?.toDouble() ?: 0.0
                resourceCount * STEAL_RICHEST_BONUS
            }

            is GameAction.DiscardResources -> {
                // Prefer discarding resources we have surplus of
                scoreDiscard(state, playerId, action.resources)
            }

            is GameAction.BankTrade ->
                TradeEvaluator.scoreBankTrade(state, action, playerId)

            is GameAction.OfferTrade ->
                TradeEvaluator.scoreOfferTrade(state, action, playerId)

            is GameAction.AcceptTrade -> {
                val trade = state.pendingTrade ?: return 0.0
                TradeEvaluator.scoreTradeResponse(state, trade, playerId, config)
            }

            is GameAction.DeclineTrade -> {
                val trade = state.pendingTrade ?: return 0.0
                val acceptScore = TradeEvaluator.scoreTradeResponse(state, trade, playerId, config)
                -acceptScore // If accepting is bad, declining is good
            }

            is GameAction.BuyDevelopmentCard ->
                DevCardEvaluator.scoreBuy(state, playerId)

            is GameAction.PlayKnight ->
                DevCardEvaluator.scorePlayKnight(state, playerId)

            is GameAction.PlayRoadBuilding ->
                DevCardEvaluator.scorePlayRoadBuilding(state, playerId)

            is GameAction.PlayYearOfPlenty ->
                DevCardEvaluator.scorePlayYearOfPlenty(state, playerId, action.resource1, action.resource2)

            is GameAction.PlayMonopoly ->
                DevCardEvaluator.scorePlayMonopoly(state, playerId, action.resource)

            is GameAction.RollDice -> 0.0

            is GameAction.EndTurn -> END_TURN_BASE_SCORE
        }
    }

    private fun scoreDiscard(state: GameState, playerId: String, resources: Map<ResourceType, Int>): Double {
        val player = state.playerById(playerId) ?: return 0.0
        var score = 0.0

        // Prefer discarding resources we have the most of
        for ((resource, amount) in resources) {
            val held = player.resources[resource] ?: 0
            // Discarding surplus is less painful
            score += (held - amount).toDouble() * 0.1
        }

        // Bonus for keeping diverse resources
        val remaining = player.resources.toMutableMap()
        for ((resource, amount) in resources) {
            remaining[resource] = (remaining[resource] ?: 0) - amount
        }
        val typesKept = remaining.count { it.value > 0 }
        score += typesKept * 0.5

        return score
    }
}
