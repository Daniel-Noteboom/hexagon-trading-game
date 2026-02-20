package com.catan.ai.heuristic

import com.catan.ai.ActionGenerator
import com.catan.ai.DifficultyConfig
import com.catan.game.GameEngine
import com.catan.game.GameEngineTestHelper
import com.catan.game.HexUtils
import com.catan.model.*
import java.util.Random
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class HeuristicAiStrategyTest {

    private val engine = GameEngine()

    @Test
    fun `setup phase returns PlaceSettlement`() {
        val state = GameEngineTestHelper.createTestState(playerCount = 2)
        val strategy = HeuristicAiStrategy(DifficultyConfig.forDifficulty(AiDifficulty.MEDIUM))
        val action = strategy.chooseAction(state, state.currentPlayer().id)
        assertTrue(action is GameAction.PlaceSettlement, "Setup should return PlaceSettlement, got ${action::class.simpleName}")
    }

    @Test
    fun `ROLL_DICE phase returns RollDice`() {
        val state = GameEngineTestHelper.completeSetup(
            GameEngineTestHelper.createTestState(playerCount = 2),
            engine
        )
        val strategy = HeuristicAiStrategy(DifficultyConfig.forDifficulty(AiDifficulty.MEDIUM))
        val action = strategy.chooseAction(state, state.currentPlayer().id)
        assertTrue(action is GameAction.RollDice, "Should return RollDice, got ${action::class.simpleName}")
    }

    @Test
    fun `TRADE_BUILD with city resources builds city`() {
        val state = GameEngineTestHelper.completeSetup(
            GameEngineTestHelper.createTestState(playerCount = 2),
            engine
        )
        state.turnPhase = TurnPhase.TRADE_BUILD
        val player = state.currentPlayer()
        player.resources.replaceAll { _, _ -> 0 }
        player.resources[ResourceType.GRAIN] = 2
        player.resources[ResourceType.ORE] = 3
        player.devCards.clear()

        val strategy = HeuristicAiStrategy(DifficultyConfig.forDifficulty(AiDifficulty.HARD), Random(42))
        val action = strategy.chooseAction(state, player.id)
        assertTrue(action is GameAction.PlaceCity, "Should build city with resources, got ${action::class.simpleName}")
    }

    @Test
    fun `TRADE_BUILD with no resources returns EndTurn`() {
        val state = GameEngineTestHelper.completeSetup(
            GameEngineTestHelper.createTestState(playerCount = 2),
            engine
        )
        state.turnPhase = TurnPhase.TRADE_BUILD
        val player = state.currentPlayer()
        player.resources.replaceAll { _, _ -> 0 }
        player.devCards.clear()

        val strategy = HeuristicAiStrategy(DifficultyConfig.forDifficulty(AiDifficulty.HARD), Random(42))
        val action = strategy.chooseAction(state, player.id)
        assertTrue(action is GameAction.EndTurn, "Should end turn with no resources, got ${action::class.simpleName}")
    }

    @Test
    fun `ROBBER_MOVE returns valid MoveRobber`() {
        val state = GameEngineTestHelper.completeSetup(
            GameEngineTestHelper.createTestState(playerCount = 2),
            engine
        )
        state.turnPhase = TurnPhase.ROBBER_MOVE

        val strategy = HeuristicAiStrategy(DifficultyConfig.forDifficulty(AiDifficulty.MEDIUM))
        val action = strategy.chooseAction(state, state.currentPlayer().id)
        assertTrue(action is GameAction.MoveRobber, "Should return MoveRobber, got ${action::class.simpleName}")
        assertTrue((action as GameAction.MoveRobber).hex != state.robberLocation)
    }

    @Test
    fun `DISCARD phase returns DiscardResources with correct count`() {
        val state = GameEngineTestHelper.completeSetup(
            GameEngineTestHelper.createTestState(playerCount = 2),
            engine
        )
        state.turnPhase = TurnPhase.DISCARD
        val player = state.currentPlayer()
        player.resources[ResourceType.BRICK] = 3
        player.resources[ResourceType.LUMBER] = 3
        player.resources[ResourceType.ORE] = 2
        player.resources[ResourceType.GRAIN] = 1
        player.resources[ResourceType.WOOL] = 1
        // Total = 10, must discard 5
        state.discardingPlayerIds = mutableListOf(player.id)

        val strategy = HeuristicAiStrategy(DifficultyConfig.forDifficulty(AiDifficulty.MEDIUM))
        val action = strategy.chooseAction(state, player.id)
        assertTrue(action is GameAction.DiscardResources)
        assertEquals(5, (action as GameAction.DiscardResources).resources.values.sum())
    }

    @Test
    fun `EASY difficulty makes suboptimal choices sometimes`() {
        val state = GameEngineTestHelper.createTestState(playerCount = 2)
        val hardStrategy = HeuristicAiStrategy(DifficultyConfig.forDifficulty(AiDifficulty.HARD), Random(0))
        val hardAction = hardStrategy.chooseAction(state, state.currentPlayer().id) as GameAction.PlaceSettlement
        val hardVertex = hardAction.vertex

        // Run EASY 100 times and count how often it picks the same vertex as HARD
        var matchCount = 0
        repeat(100) { seed ->
            val easyStrategy = HeuristicAiStrategy(DifficultyConfig.forDifficulty(AiDifficulty.EASY), Random(seed.toLong()))
            val easyAction = easyStrategy.chooseAction(state, state.currentPlayer().id) as GameAction.PlaceSettlement
            if (easyAction.vertex == hardVertex) matchCount++
        }

        assertTrue(matchCount < 90, "EASY should be non-deterministic: matched HARD $matchCount/100 times")
    }

    @Test
    fun `HARD difficulty is nearly deterministic`() {
        val state = GameEngineTestHelper.createTestState(playerCount = 2)
        val referenceStrategy = HeuristicAiStrategy(DifficultyConfig.forDifficulty(AiDifficulty.HARD), Random(0))
        val referenceAction = referenceStrategy.chooseAction(state, state.currentPlayer().id) as GameAction.PlaceSettlement
        val referenceVertex = referenceAction.vertex

        var matchCount = 0
        repeat(100) { seed ->
            val strategy = HeuristicAiStrategy(DifficultyConfig.forDifficulty(AiDifficulty.HARD), Random(seed.toLong()))
            val action = strategy.chooseAction(state, state.currentPlayer().id) as GameAction.PlaceSettlement
            if (action.vertex == referenceVertex) matchCount++
        }

        assertTrue(matchCount > 95, "HARD should be nearly deterministic: matched $matchCount/100 times")
    }

    @Test
    fun `invariant - chooseAction never throws for random game states`() {
        val strategy = HeuristicAiStrategy(DifficultyConfig.forDifficulty(AiDifficulty.MEDIUM))

        repeat(50) { seed ->
            val state = createRandomState(seed)
            val playerId = findActionablePlayer(state) ?: return@repeat

            val action = strategy.chooseAction(state, playerId)
            assertNotNull(action, "Should return an action for seed $seed")
        }
    }

    @Test
    fun `invariant - returned action always succeeds in GameEngine`() {
        val strategy = HeuristicAiStrategy(DifficultyConfig.forDifficulty(AiDifficulty.MEDIUM))

        repeat(50) { seed ->
            val state = createRandomState(seed)
            val playerId = findActionablePlayer(state) ?: return@repeat

            val action = strategy.chooseAction(state, playerId)
            val stateCopy = state.deepCopy()
            val result = engine.execute(stateCopy, action)
            assertTrue(
                result.isSuccess,
                "Action ${action::class.simpleName} failed for seed $seed: ${result.exceptionOrNull()?.message}"
            )
        }
    }

    private fun findActionablePlayer(state: GameState): String? {
        if (state.phase == GamePhase.FINISHED || state.phase == GamePhase.LOBBY) return null
        if (state.turnPhase == TurnPhase.DISCARD && state.discardingPlayerIds.isNotEmpty()) {
            return state.discardingPlayerIds.first()
        }
        if (state.pendingTrade != null) {
            val trade = state.pendingTrade!!
            return trade.toPlayerId ?: state.players.first { it.id != trade.fromPlayerId }.id
        }
        return state.currentPlayer().id
    }

    private fun createRandomState(seed: Int): GameState {
        val state = GameEngineTestHelper.completeSetup(
            GameEngineTestHelper.createTestState(playerCount = 2 + (seed % 3)),
            engine
        )
        var current = state
        val random = Random(seed.toLong())

        repeat(random.nextInt(15)) {
            val playerId = findActionablePlayer(current) ?: return current
            val actions = ActionGenerator.generate(current, playerId)
            if (actions.isEmpty()) return current
            val action = actions[random.nextInt(actions.size)]
            val result = engine.execute(current, action)
            if (result.isSuccess) current = result.getOrThrow()
            if (current.phase == GamePhase.FINISHED) return current
        }
        return current
    }

    private fun GameState.deepCopy(): GameState {
        return this.copy(
            buildings = this.buildings.toMutableList(),
            roads = this.roads.toMutableList(),
            players = this.players.map {
                it.copy(
                    resources = it.resources.toMutableMap(),
                    devCards = it.devCards.toMutableList(),
                    newDevCards = it.newDevCards.toMutableList()
                )
            }.toMutableList(),
            devCardDeck = this.devCardDeck.toMutableList(),
            discardingPlayerIds = this.discardingPlayerIds.toMutableList()
        )
    }
}
