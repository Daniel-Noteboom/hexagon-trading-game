package com.catan.game

import com.catan.model.*
import kotlin.test.*

class GameEngineTradeTest {

    private val engine = GameEngine()

    private fun setupMainPhaseState(): GameState {
        val state = GameEngineTestHelper.createTestState(playerCount = 2)
        return GameEngineTestHelper.completeSetup(state, engine)
    }

    @Test
    fun `offer trade creates pending trade`() {
        val state = setupMainPhaseState()
        state.turnPhase = TurnPhase.TRADE_BUILD
        val player = state.playerById("player0")!!
        player.resources[ResourceType.BRICK] = 2

        val result = engine.execute(state, GameAction.OfferTrade(
            playerId = "player0",
            offering = mapOf(ResourceType.BRICK to 2),
            requesting = mapOf(ResourceType.ORE to 1)
        ))
        assertTrue(result.isSuccess)
        assertNotNull(state.pendingTrade)
        assertEquals("player0", state.pendingTrade!!.fromPlayerId)
    }

    @Test
    fun `accept trade swaps resources between players`() {
        val state = setupMainPhaseState()
        state.turnPhase = TurnPhase.TRADE_BUILD
        val p0 = state.playerById("player0")!!
        val p1 = state.playerById("player1")!!

        p0.resources.replaceAll { _, _ -> 0 }
        p1.resources.replaceAll { _, _ -> 0 }
        p0.resources[ResourceType.BRICK] = 2
        p1.resources[ResourceType.ORE] = 1

        engine.execute(state, GameAction.OfferTrade(
            playerId = "player0",
            offering = mapOf(ResourceType.BRICK to 2),
            requesting = mapOf(ResourceType.ORE to 1)
        )).getOrThrow()

        engine.execute(state, GameAction.AcceptTrade("player1")).getOrThrow()

        assertEquals(0, p0.resources[ResourceType.BRICK])
        assertEquals(1, p0.resources[ResourceType.ORE])
        assertEquals(2, p1.resources[ResourceType.BRICK])
        assertEquals(0, p1.resources[ResourceType.ORE])
        assertNull(state.pendingTrade)
    }

    @Test
    fun `decline trade removes offer`() {
        val state = setupMainPhaseState()
        state.turnPhase = TurnPhase.TRADE_BUILD
        val player = state.playerById("player0")!!
        player.resources[ResourceType.BRICK] = 2

        engine.execute(state, GameAction.OfferTrade(
            playerId = "player0",
            offering = mapOf(ResourceType.BRICK to 2),
            requesting = mapOf(ResourceType.ORE to 1)
        )).getOrThrow()

        engine.execute(state, GameAction.DeclineTrade("player1")).getOrThrow()
        assertNull(state.pendingTrade)
    }

    @Test
    fun `bank trade at default ratio works`() {
        val state = setupMainPhaseState()
        state.turnPhase = TurnPhase.TRADE_BUILD
        // Remove all ports so we know ratio is 4:1
        val noPortState = state.copy(ports = emptyList())
        val player = noPortState.playerById("player0")!!
        player.resources[ResourceType.BRICK] = 4
        val oreBefore = player.resources[ResourceType.ORE] ?: 0

        val result = engine.execute(noPortState, GameAction.BankTrade(
            playerId = "player0",
            giving = ResourceType.BRICK,
            givingAmount = 4,
            receiving = ResourceType.ORE
        ))
        assertTrue(result.isSuccess)
        assertEquals(0, player.resources[ResourceType.BRICK])
        assertEquals(oreBefore + 1, player.resources[ResourceType.ORE])
    }

    @Test
    fun `trade with insufficient resources fails`() {
        val state = setupMainPhaseState()
        state.turnPhase = TurnPhase.TRADE_BUILD
        val player = state.playerById("player0")!!
        player.resources[ResourceType.BRICK] = 1

        val result = engine.execute(state, GameAction.OfferTrade(
            playerId = "player0",
            offering = mapOf(ResourceType.BRICK to 5),
            requesting = mapOf(ResourceType.ORE to 1)
        ))
        assertTrue(result.isFailure)
    }

    @Test
    fun `bank trade with wrong ratio fails`() {
        val state = setupMainPhaseState()
        state.turnPhase = TurnPhase.TRADE_BUILD
        // Remove ports to ensure 4:1 ratio
        val noPortState = state.copy(ports = emptyList())
        val player = noPortState.playerById("player0")!!
        player.resources[ResourceType.BRICK] = 3

        val result = engine.execute(noPortState, GameAction.BankTrade(
            playerId = "player0",
            giving = ResourceType.BRICK,
            givingAmount = 3,
            receiving = ResourceType.ORE
        ))
        // Without a 3:1 port, ratio should be 4:1, so giving 3 should fail
        assertTrue(result.isFailure)
    }
}
