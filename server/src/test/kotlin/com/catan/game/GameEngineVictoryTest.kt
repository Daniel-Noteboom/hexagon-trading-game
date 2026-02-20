package com.catan.game

import com.catan.model.*
import kotlin.test.*

class GameEngineVictoryTest {

    private val engine = GameEngine()

    private fun setupMainPhaseState(): GameState {
        val state = GameEngineTestHelper.createTestState(playerCount = 2)
        return GameEngineTestHelper.completeSetup(state, engine)
    }

    @Test
    fun `player with 10 or more VP wins`() {
        val state = setupMainPhaseState()
        state.turnPhase = TurnPhase.TRADE_BUILD
        val player = state.playerById("player0")!!

        // Set player to 9 VP, then do an action that gives 1 more
        player.victoryPoints = 9
        player.resources[ResourceType.GRAIN] = 2
        player.resources[ResourceType.ORE] = 3

        // Upgrade a settlement to city (+1 VP)
        val settlement = state.buildings.first { it.playerId == "player0" && it.type == BuildingType.SETTLEMENT }
        val result = engine.execute(state, GameAction.PlaceCity("player0", settlement.vertex))
        assertTrue(result.isSuccess)
        assertEquals(10, player.victoryPoints)
        assertEquals(GamePhase.FINISHED, state.phase)
    }

    @Test
    fun `game does not end before 10 VP`() {
        val state = setupMainPhaseState()
        state.turnPhase = TurnPhase.TRADE_BUILD
        val player = state.playerById("player0")!!

        player.victoryPoints = 8
        player.resources[ResourceType.GRAIN] = 2
        player.resources[ResourceType.ORE] = 3

        val settlement = state.buildings.first { it.playerId == "player0" && it.type == BuildingType.SETTLEMENT }
        engine.execute(state, GameAction.PlaceCity("player0", settlement.vertex)).getOrThrow()
        assertEquals(9, player.victoryPoints)
        assertEquals(GamePhase.MAIN, state.phase) // Not finished yet
    }

    @Test
    fun `largest army with 3 or more knights awards 2 VP`() {
        val state = setupMainPhaseState()
        state.turnPhase = TurnPhase.TRADE_BUILD
        val player = state.playerById("player0")!!

        // Play 3 knights manually
        player.knightsPlayed = 2
        player.devCards.add(DevelopmentCardType.KNIGHT)

        val vpBefore = player.victoryPoints
        engine.execute(state, GameAction.PlayKnight("player0")).getOrThrow()

        assertEquals(3, player.knightsPlayed)
        assertEquals("player0", state.largestArmyHolder)
        assertEquals(vpBefore + 2, player.victoryPoints) // +2 for largest army
    }

    @Test
    fun `largest army can be stolen`() {
        val state = setupMainPhaseState()
        state.turnPhase = TurnPhase.TRADE_BUILD

        val p0 = state.playerById("player0")!!
        val p1 = state.playerById("player1")!!

        // P0 has largest army (3 knights)
        p0.knightsPlayed = 3
        state.largestArmyHolder = "player0"
        p0.victoryPoints += 2

        // P1 plays 4th knight to steal
        p1.knightsPlayed = 3
        p1.devCards.add(DevelopmentCardType.KNIGHT)

        // End p0's turn
        engine.execute(state, GameAction.EndTurn("player0")).getOrThrow()

        // Now it's p1's turn
        assertEquals(1, state.currentPlayerIndex)
        state.turnPhase = TurnPhase.TRADE_BUILD // Skip dice for test

        val p0VpBefore = p0.victoryPoints
        val p1VpBefore = p1.victoryPoints

        engine.execute(state, GameAction.PlayKnight("player1")).getOrThrow()

        assertEquals("player1", state.largestArmyHolder)
        assertEquals(p0VpBefore - 2, p0.victoryPoints) // P0 loses 2 VP
        assertEquals(p1VpBefore + 2, p1.victoryPoints) // P1 gains 2 VP
    }

    @Test
    fun `VP dev cards count toward victory`() {
        val state = setupMainPhaseState()
        state.turnPhase = TurnPhase.TRADE_BUILD
        val player = state.playerById("player0")!!

        player.victoryPoints = 9
        player.resources[ResourceType.ORE] = 1
        player.resources[ResourceType.GRAIN] = 1
        player.resources[ResourceType.WOOL] = 1

        state.devCardDeck.clear()
        state.devCardDeck.add(DevelopmentCardType.VICTORY_POINT)

        engine.execute(state, GameAction.BuyDevelopmentCard("player0")).getOrThrow()
        assertEquals(10, player.victoryPoints)
        assertEquals(GamePhase.FINISHED, state.phase)
    }

    @Test
    fun `end turn advances to next player`() {
        val state = setupMainPhaseState()
        state.turnPhase = TurnPhase.TRADE_BUILD

        engine.execute(state, GameAction.EndTurn("player0")).getOrThrow()
        assertEquals(1, state.currentPlayerIndex)
        assertEquals(TurnPhase.ROLL_DICE, state.turnPhase)
    }

    @Test
    fun `end turn wraps around player index`() {
        val state = setupMainPhaseState()
        state.turnPhase = TurnPhase.TRADE_BUILD
        state.currentPlayerIndex = 1

        engine.execute(state, GameAction.EndTurn("player1")).getOrThrow()
        assertEquals(0, state.currentPlayerIndex)
    }

    @Test
    fun `new dev cards become playable after end turn`() {
        val state = setupMainPhaseState()
        state.turnPhase = TurnPhase.TRADE_BUILD
        val player = state.playerById("player0")!!

        player.newDevCards.add(DevelopmentCardType.KNIGHT)
        assertEquals(0, player.devCards.size)

        engine.execute(state, GameAction.EndTurn("player0")).getOrThrow()
        // After full round, when it's player0's turn again, cards moved
        // Actually, cards move at end of the turn they were bought
        assertEquals(1, player.devCards.size)
        assertEquals(0, player.newDevCards.size)
    }
}
