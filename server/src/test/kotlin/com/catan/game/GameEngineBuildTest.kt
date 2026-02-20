package com.catan.game

import com.catan.model.*
import kotlin.test.*

class GameEngineBuildTest {

    private val engine = GameEngine()

    private fun setupMainPhaseState(): GameState {
        val state = GameEngineTestHelper.createTestState(playerCount = 2)
        return GameEngineTestHelper.completeSetup(state, engine)
    }

    @Test
    fun `build settlement deducts correct resources`() {
        val state = setupMainPhaseState()
        state.turnPhase = TurnPhase.TRADE_BUILD
        val player = state.playerById("player0")!!

        // Give player resources
        player.resources[ResourceType.BRICK] = 1
        player.resources[ResourceType.LUMBER] = 1
        player.resources[ResourceType.GRAIN] = 1
        player.resources[ResourceType.WOOL] = 1

        // Find a vertex adjacent to player's existing road
        val playerRoad = state.roads.first { it.playerId == "player0" }
        val roadVertices = HexUtils.verticesOfEdge(playerRoad.edge)
        val validVertex = roadVertices.firstOrNull { v ->
            state.buildingAt(v) == null && HexUtils.adjacentVertices(v).none { state.buildingAt(it) != null }
        }

        if (validVertex != null) {
            val result = engine.execute(state, GameAction.PlaceSettlement("player0", validVertex))
            assertTrue(result.isSuccess)
            assertEquals(0, player.resources[ResourceType.BRICK])
            assertEquals(0, player.resources[ResourceType.LUMBER])
            assertEquals(0, player.resources[ResourceType.GRAIN])
            assertEquals(0, player.resources[ResourceType.WOOL])
        }
    }

    @Test
    fun `build settlement fails with insufficient resources`() {
        val state = setupMainPhaseState()
        state.turnPhase = TurnPhase.TRADE_BUILD
        val player = state.playerById("player0")!!
        // Player has 0 resources
        player.resources.keys.forEach { player.resources[it] = 0 }

        val vertex = GameEngineTestHelper.findValidVertex(state)
        val result = engine.execute(state, GameAction.PlaceSettlement("player0", vertex))
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("Insufficient") == true)
    }

    @Test
    fun `build settlement fails if not adjacent to own road`() {
        val state = setupMainPhaseState()
        state.turnPhase = TurnPhase.TRADE_BUILD
        val player = state.playerById("player0")!!

        player.resources[ResourceType.BRICK] = 1
        player.resources[ResourceType.LUMBER] = 1
        player.resources[ResourceType.GRAIN] = 1
        player.resources[ResourceType.WOOL] = 1

        // Find a vertex far from player's roads
        val playerEdges = state.roads.filter { it.playerId == "player0" }.map { it.edge }.toSet()
        val playerVertices = playerEdges.flatMap { HexUtils.verticesOfEdge(it) }.toSet()
        val allConnected = playerVertices.flatMap { HexUtils.edgesOfVertex(it) }.toSet()
        val allConnectedVertices = allConnected.flatMap { HexUtils.verticesOfEdge(it) }.toSet()

        val farVertex = HexUtils.ALL_VERTICES.firstOrNull { v ->
            v !in allConnectedVertices &&
            state.buildingAt(v) == null &&
            HexUtils.adjacentVertices(v).none { state.buildingAt(it) != null }
        }

        if (farVertex != null) {
            val result = engine.execute(state, GameAction.PlaceSettlement("player0", farVertex))
            assertTrue(result.isFailure)
            assertTrue(result.exceptionOrNull()?.message?.contains("adjacent to your own road") == true)
        }
    }

    @Test
    fun `build city upgrades existing settlement`() {
        val state = setupMainPhaseState()
        state.turnPhase = TurnPhase.TRADE_BUILD
        val player = state.playerById("player0")!!

        player.resources[ResourceType.GRAIN] = 2
        player.resources[ResourceType.ORE] = 3

        val settlement = state.buildings.first { it.playerId == "player0" && it.type == BuildingType.SETTLEMENT }
        val vpBefore = player.victoryPoints

        val result = engine.execute(state, GameAction.PlaceCity("player0", settlement.vertex))
        assertTrue(result.isSuccess)
        assertEquals(BuildingType.CITY, state.buildingAt(settlement.vertex)!!.type)
        assertEquals(0, player.resources[ResourceType.GRAIN])
        assertEquals(0, player.resources[ResourceType.ORE])
        assertEquals(vpBefore + 1, player.victoryPoints)
    }

    @Test
    fun `build city on non-owned vertex fails`() {
        val state = setupMainPhaseState()
        state.turnPhase = TurnPhase.TRADE_BUILD
        val player = state.playerById("player0")!!

        player.resources[ResourceType.GRAIN] = 2
        player.resources[ResourceType.ORE] = 3

        val opponentSettlement = state.buildings.first { it.playerId == "player1" }

        val result = engine.execute(state, GameAction.PlaceCity("player0", opponentSettlement.vertex))
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("Not your building") == true)
    }

    @Test
    fun `build road deducts 1 brick and 1 lumber`() {
        val state = setupMainPhaseState()
        state.turnPhase = TurnPhase.TRADE_BUILD
        val player = state.playerById("player0")!!

        player.resources[ResourceType.BRICK] = 1
        player.resources[ResourceType.LUMBER] = 1

        // Find a valid edge extending from player's existing network
        val existingRoad = state.roads.first { it.playerId == "player0" }
        val roadVertices = HexUtils.verticesOfEdge(existingRoad.edge)
        val extendVertex = roadVertices.first()
        val adjEdges = HexUtils.edgesOfVertex(extendVertex)
        val newEdge = adjEdges.firstOrNull { state.roadAt(it) == null && it != existingRoad.edge }

        if (newEdge != null) {
            val result = engine.execute(state, GameAction.PlaceRoad("player0", newEdge))
            assertTrue(result.isSuccess)
            assertEquals(0, player.resources[ResourceType.BRICK])
            assertEquals(0, player.resources[ResourceType.LUMBER])
        }
    }

    @Test
    fun `build road must extend from existing road or settlement`() {
        val state = setupMainPhaseState()
        state.turnPhase = TurnPhase.TRADE_BUILD
        val player = state.playerById("player0")!!

        player.resources[ResourceType.BRICK] = 1
        player.resources[ResourceType.LUMBER] = 1

        // Find an edge completely disconnected from player's network
        val playerEdges = state.roads.filter { it.playerId == "player0" }.map { it.edge }.toSet()
        val playerBuildings = state.buildings.filter { it.playerId == "player0" }.map { it.vertex }.toSet()
        val connectedVertices = (playerEdges.flatMap { HexUtils.verticesOfEdge(it) } + playerBuildings).toSet()
        val connectedEdges = connectedVertices.flatMap { HexUtils.edgesOfVertex(it) }.toSet()

        val disconnectedEdge = HexUtils.ALL_EDGES.firstOrNull { it !in connectedEdges && state.roadAt(it) == null }

        if (disconnectedEdge != null) {
            val result = engine.execute(state, GameAction.PlaceRoad("player0", disconnectedEdge))
            assertTrue(result.isFailure)
            assertTrue(result.exceptionOrNull()?.message?.contains("connect") == true)
        }
    }
}
