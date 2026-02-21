package com.catan.ai

import com.catan.ai.heuristic.HeuristicAiStrategy
import com.catan.game.GameEngine
import com.catan.model.*
import kotlinx.coroutines.delay

class AiController(
    private val engine: GameEngine
) {
    private val strategies: Map<AiDifficulty, AiStrategy> = AiDifficulty.entries.associateWith { difficulty ->
        HeuristicAiStrategy(DifficultyConfig.forDifficulty(difficulty))
    }

    suspend fun onStateChanged(
        gameId: String,
        state: GameState,
        saveState: suspend (String, GameState) -> Unit,
        broadcastState: suspend (GameState) -> Unit,
        delayMs: Long = 300L
    ) {
        var currentState = state
        var actionCount = 0
        val maxActions = 500

        while (currentState.phase != GamePhase.FINISHED && actionCount < maxActions) {
            val aiAction = findNextAiAction(currentState) ?: break

            if (delayMs > 0) delay(delayMs)

            val result = engine.execute(currentState, aiAction)
            if (result.isFailure) break

            currentState = result.getOrThrow()
            actionCount++

            saveState(gameId, currentState)
            broadcastState(currentState)
        }
    }

    fun executeAiActions(state: GameState, maxTurns: Int = 500): GameState {
        var currentState = state
        var turnCount = 0
        var actionCount = 0
        val maxActionsPerTurn = 20
        var actionsThisTurn = 0
        var lastPlayerIndex = state.currentPlayerIndex
        var consecutiveFailures = 0

        while (currentState.phase != GamePhase.FINISHED && turnCount < maxTurns * currentState.players.size) {
            val aiAction = findNextAiAction(currentState) ?: break

            val result = engine.execute(currentState, aiAction)
            if (result.isFailure) {
                consecutiveFailures++
                if (consecutiveFailures >= 3) break
                continue
            }

            consecutiveFailures = 0
            currentState = result.getOrThrow()
            actionCount++
            actionsThisTurn++

            // Track turn changes
            if (currentState.currentPlayerIndex != lastPlayerIndex) {
                turnCount++
                actionsThisTurn = 0
                lastPlayerIndex = currentState.currentPlayerIndex
            }

            // Safety: force end turn if too many actions in one turn
            if (actionsThisTurn >= maxActionsPerTurn && currentState.turnPhase == TurnPhase.TRADE_BUILD && currentState.roadBuildingRoadsLeft == 0) {
                val endTurn = GameAction.EndTurn(currentState.currentPlayer().id)
                val endResult = engine.execute(currentState, endTurn)
                if (endResult.isSuccess) {
                    currentState = endResult.getOrThrow()
                    turnCount++
                    actionsThisTurn = 0
                    lastPlayerIndex = currentState.currentPlayerIndex
                }
            }
        }
        return currentState
    }

    private fun findNextAiAction(state: GameState): GameAction? {
        if (state.phase == GamePhase.FINISHED || state.phase == GamePhase.LOBBY) return null

        // Handle discard phase — check all AI players who need to discard
        if (state.turnPhase == TurnPhase.DISCARD && state.discardingPlayerIds.isNotEmpty()) {
            for (discardPlayerId in state.discardingPlayerIds.toList()) {
                val discardPlayer = state.playerById(discardPlayerId) ?: continue
                if (discardPlayer.isAi) {
                    val strategy = getStrategy(discardPlayer)
                    return strategy.chooseAction(state, discardPlayer.id)
                }
            }
            return null
        }

        // Handle pending trade responses from AI
        if (state.pendingTrade != null) {
            val trade = state.pendingTrade!!
            for (player in state.players) {
                if (!player.isAi) continue
                if (player.id == trade.fromPlayerId) continue
                if (trade.toPlayerId != null && trade.toPlayerId != player.id) continue

                val strategy = getStrategy(player)
                return strategy.chooseAction(state, player.id)
            }
            return null
        }

        // Normal turn — check if current player is AI
        val currentPlayer = state.currentPlayer()
        if (!currentPlayer.isAi) return null

        val strategy = getStrategy(currentPlayer)
        return strategy.chooseAction(state, currentPlayer.id)
    }

    private fun getStrategy(player: Player): AiStrategy {
        return strategies[player.aiDifficulty] ?: strategies[AiDifficulty.MEDIUM]!!
    }
}
