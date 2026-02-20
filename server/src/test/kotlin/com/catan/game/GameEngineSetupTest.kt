package com.catan.game

import com.catan.model.*
import kotlin.test.*

class GameEngineSetupTest {

    private val engine = GameEngine()

    @Test
    fun `place settlement during setup - valid placement succeeds`() {
        val state = GameEngineTestHelper.createTestState()
        val vertex = GameEngineTestHelper.findValidVertex(state)

        val result = engine.execute(state, GameAction.PlaceSettlement("player0", vertex))
        assertTrue(result.isSuccess)
        val newState = result.getOrThrow()
        assertNotNull(newState.buildingAt(vertex))
        assertEquals("player0", newState.buildingAt(vertex)!!.playerId)
        assertEquals(BuildingType.SETTLEMENT, newState.buildingAt(vertex)!!.type)
    }

    @Test
    fun `place settlement on occupied vertex fails`() {
        val state = GameEngineTestHelper.createTestState()
        val vertex = GameEngineTestHelper.findValidVertex(state)

        // Place first settlement
        state.buildings.add(Building(vertex, "player1", BuildingType.SETTLEMENT))

        val result = engine.execute(state, GameAction.PlaceSettlement("player0", vertex))
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("occupied") == true)
    }

    @Test
    fun `place settlement violating distance rule fails`() {
        val state = GameEngineTestHelper.createTestState()
        val vertex = GameEngineTestHelper.findValidVertex(state)

        // Place a settlement
        engine.execute(state, GameAction.PlaceSettlement("player0", vertex)).getOrThrow()
        val edge = GameEngineTestHelper.findEdgeAdjacentToVertex(state, vertex)
        engine.execute(state, GameAction.PlaceRoad("player0", edge)).getOrThrow()

        // Now try to place in next setup turn (player1) at an adjacent vertex
        val adjVertex = HexUtils.adjacentVertices(vertex).firstOrNull { it in HexUtils.ALL_VERTICES }
        if (adjVertex != null) {
            val result = engine.execute(state, GameAction.PlaceSettlement("player1", adjVertex))
            assertTrue(result.isFailure)
            assertTrue(result.exceptionOrNull()?.message?.contains("distance") == true)
        }
    }

    @Test
    fun `place road during setup must be adjacent to just-placed settlement`() {
        val state = GameEngineTestHelper.createTestState()
        val vertex = GameEngineTestHelper.findValidVertex(state)

        engine.execute(state, GameAction.PlaceSettlement("player0", vertex)).getOrThrow()

        // Try placing road NOT adjacent to the settlement
        val nonAdjacentEdge = HexUtils.ALL_EDGES.first { edge ->
            vertex !in HexUtils.verticesOfEdge(edge)
        }
        val result = engine.execute(state, GameAction.PlaceRoad("player0", nonAdjacentEdge))
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("adjacent") == true)
    }

    @Test
    fun `setup forward completes when all players have placed`() {
        val state = GameEngineTestHelper.createTestState(playerCount = 2)
        val usedVertices = mutableSetOf<VertexCoord>()

        // Player 0 places
        val v0 = GameEngineTestHelper.findValidVertex(state, usedVertices)
        usedVertices.add(v0)
        usedVertices.addAll(HexUtils.adjacentVertices(v0))
        engine.execute(state, GameAction.PlaceSettlement("player0", v0)).getOrThrow()
        val e0 = GameEngineTestHelper.findEdgeAdjacentToVertex(state, v0)
        engine.execute(state, GameAction.PlaceRoad("player0", e0)).getOrThrow()

        // Should be player1's turn now
        assertEquals(1, state.currentPlayerIndex)

        // Player 1 places
        val v1 = GameEngineTestHelper.findValidVertex(state, usedVertices)
        usedVertices.add(v1)
        usedVertices.addAll(HexUtils.adjacentVertices(v1))
        engine.execute(state, GameAction.PlaceSettlement("player1", v1)).getOrThrow()
        val e1 = GameEngineTestHelper.findEdgeAdjacentToVertex(state, v1)
        engine.execute(state, GameAction.PlaceRoad("player1", e1)).getOrThrow()

        // Should now be in SETUP_REVERSE, still player1's turn
        assertEquals(GamePhase.SETUP_REVERSE, state.phase)
        assertEquals(1, state.currentPlayerIndex)
    }

    @Test
    fun `setup reverse grants starting resources from second settlement`() {
        val state = GameEngineTestHelper.createTestState(playerCount = 2)
        val completed = GameEngineTestHelper.completeSetup(state, engine)

        // In reverse setup, each player's second settlement should grant resources
        // from adjacent hexes. Check that at least one player has some resources.
        val totalResources = completed.players.sumOf { it.totalResourceCount() }
        assertTrue(totalResources > 0, "Players should have starting resources after setup reverse")
    }

    @Test
    fun `full setup completes and transitions to MAIN phase`() {
        val state = GameEngineTestHelper.createTestState(playerCount = 2)
        val completed = GameEngineTestHelper.completeSetup(state, engine)

        assertEquals(GamePhase.MAIN, completed.phase)
        assertEquals(TurnPhase.ROLL_DICE, completed.turnPhase)
        assertEquals(0, completed.currentPlayerIndex)
    }

    @Test
    fun `setup with 3 players works correctly`() {
        val state = GameEngineTestHelper.createTestState(playerCount = 3)
        val completed = GameEngineTestHelper.completeSetup(state, engine)

        assertEquals(GamePhase.MAIN, completed.phase)
        // Each player should have 2 settlements and 2 roads
        for (player in completed.players) {
            val settlements = completed.buildings.count { it.playerId == player.id }
            val roads = completed.roads.count { it.playerId == player.id }
            assertEquals(2, settlements, "${player.displayName} should have 2 settlements")
            assertEquals(2, roads, "${player.displayName} should have 2 roads")
        }
    }

    @Test
    fun `setup with 4 players works correctly`() {
        val state = GameEngineTestHelper.createTestState(playerCount = 4)
        val completed = GameEngineTestHelper.completeSetup(state, engine)

        assertEquals(GamePhase.MAIN, completed.phase)
        assertEquals(8, completed.buildings.size) // 4 players * 2 settlements
        assertEquals(8, completed.roads.size) // 4 players * 2 roads
    }

    @Test
    fun `cannot place settlement when not your turn`() {
        val state = GameEngineTestHelper.createTestState()
        val vertex = GameEngineTestHelper.findValidVertex(state)

        // player1 tries to act when it's player0's turn
        val result = engine.execute(state, GameAction.PlaceSettlement("player1", vertex))
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("Not your turn") == true)
    }

    @Test
    fun `cannot place road before settlement in setup`() {
        val state = GameEngineTestHelper.createTestState()
        val edge = HexUtils.ALL_EDGES.first()

        val result = engine.execute(state, GameAction.PlaceRoad("player0", edge))
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("settlement before road") == true)
    }

    @Test
    fun `each player gets exactly 1 VP per settlement during setup`() {
        val state = GameEngineTestHelper.createTestState(playerCount = 2)
        val completed = GameEngineTestHelper.completeSetup(state, engine)

        for (player in completed.players) {
            // Each player has 2 settlements = 2 VP (before any resource-based VP)
            assertTrue(player.victoryPoints >= 2, "${player.displayName} should have at least 2 VP from settlements")
        }
    }
}
