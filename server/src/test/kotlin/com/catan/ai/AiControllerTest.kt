package com.catan.ai

import com.catan.game.GameEngine
import com.catan.game.GameEngineTestHelper
import com.catan.model.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AiControllerTest {

    private val engine = GameEngine()
    private val controller = AiController(engine)

    private fun createAiPlayer(id: String, name: String, color: PlayerColor, difficulty: AiDifficulty = AiDifficulty.MEDIUM): Player {
        return Player(
            id = id,
            displayName = name,
            color = color,
            isAi = true,
            aiDifficulty = difficulty
        )
    }

    private fun createMixedState(): GameState {
        val state = GameEngineTestHelper.createTestState(playerCount = 2)
        state.players[1] = createAiPlayer("player1", "Bot Alice", PlayerColor.BLUE)
        return state
    }

    @Test
    fun `all-AI game completes from setup to finish`() {
        val state = GameEngineTestHelper.createTestState(playerCount = 2)
        state.players[0] = createAiPlayer("player0", "Bot 1", PlayerColor.RED)
        state.players[1] = createAiPlayer("player1", "Bot 2", PlayerColor.BLUE)

        val result = controller.executeAiActions(state)

        // Game should reach FINISHED
        assertEquals(GamePhase.FINISHED, result.phase, "AI should play through to finish")
        // Each player should have at least 2 settlements (from setup)
        for (player in result.players) {
            val buildings = result.buildings.count { it.playerId == player.id }
            val roads = result.roads.count { it.playerId == player.id }
            assertTrue(buildings >= 2, "Player ${player.id} should have at least 2 buildings, has $buildings")
            assertTrue(roads >= 2, "Player ${player.id} should have at least 2 roads, has $roads")
        }
    }

    @Test
    fun `AI handles discard when it has more than 7 cards`() {
        val state = GameEngineTestHelper.completeSetup(
            GameEngineTestHelper.createTestState(playerCount = 2).also {
                it.players[0] = createAiPlayer("player0", "Bot 0", PlayerColor.RED)
                it.players[1] = createAiPlayer("player1", "Bot 1", PlayerColor.BLUE)
            },
            engine
        )

        state.turnPhase = TurnPhase.DISCARD
        val aiPlayer = state.players[0]
        aiPlayer.resources[ResourceType.BRICK] = 3
        aiPlayer.resources[ResourceType.LUMBER] = 3
        aiPlayer.resources[ResourceType.ORE] = 2
        aiPlayer.resources[ResourceType.GRAIN] = 1
        aiPlayer.resources[ResourceType.WOOL] = 1
        state.discardingPlayerIds = mutableListOf(aiPlayer.id)

        val result = controller.executeAiActions(state)
        // AI should have processed the discard (may have played further too)
        assertTrue(!result.discardingPlayerIds.contains(aiPlayer.id),
            "AI should have discarded")
    }

    @Test
    fun `AI responds to pending trade`() {
        val state = GameEngineTestHelper.completeSetup(
            createMixedState().also {
                it.players[1] = createAiPlayer("player1", "Bot Alice", PlayerColor.BLUE)
            },
            engine
        )
        state.turnPhase = TurnPhase.TRADE_BUILD
        val humanPlayer = state.players[0]
        val aiPlayer = state.players[1]
        // Ensure both players have the traded resources
        humanPlayer.resources[ResourceType.BRICK] = 3
        aiPlayer.resources[ResourceType.ORE] = 3

        state.pendingTrade = TradeOffer(
            fromPlayerId = "player0",
            toPlayerId = "player1",
            offering = mapOf(ResourceType.BRICK to 1),
            requesting = mapOf(ResourceType.ORE to 1)
        )

        val result = controller.executeAiActions(state)
        // Trade should be resolved (accepted or declined)
        assertTrue(result.pendingTrade == null, "AI should resolve pending trade")
    }

    @Test
    fun `two consecutive AI players both complete game`() {
        val state = GameEngineTestHelper.createTestState(playerCount = 2)
        state.players[0] = createAiPlayer("player0", "Bot 1", PlayerColor.RED)
        state.players[1] = createAiPlayer("player1", "Bot 2", PlayerColor.BLUE)

        val result = controller.executeAiActions(state)
        val bot1Buildings = result.buildings.count { it.playerId == "player0" }
        val bot2Buildings = result.buildings.count { it.playerId == "player1" }
        assertTrue(bot1Buildings >= 2, "Bot 1 should have at least 2 buildings")
        assertTrue(bot2Buildings >= 2, "Bot 2 should have at least 2 buildings")
    }

    @Test
    fun `human player stops AI execution`() {
        val state = GameEngineTestHelper.completeSetup(
            createMixedState(),
            engine
        )
        // Player0 (human) is current, should not execute AI
        assertEquals("player0", state.currentPlayer().id)

        val result = controller.executeAiActions(state)
        // State should be unchanged since current player is human
        assertEquals(TurnPhase.ROLL_DICE, result.turnPhase)
    }
}
