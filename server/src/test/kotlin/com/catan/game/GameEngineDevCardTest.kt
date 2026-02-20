package com.catan.game

import com.catan.model.*
import kotlin.test.*

class GameEngineDevCardTest {

    private val engine = GameEngine()

    private fun setupMainPhaseState(): GameState {
        val state = GameEngineTestHelper.createTestState(playerCount = 2)
        return GameEngineTestHelper.completeSetup(state, engine)
    }

    @Test
    fun `buy dev card deducts 1 ore, 1 grain, 1 wool`() {
        val state = setupMainPhaseState()
        state.turnPhase = TurnPhase.TRADE_BUILD
        val player = state.playerById("player0")!!

        player.resources[ResourceType.ORE] = 1
        player.resources[ResourceType.GRAIN] = 1
        player.resources[ResourceType.WOOL] = 1

        val result = engine.execute(state, GameAction.BuyDevelopmentCard("player0"))
        assertTrue(result.isSuccess)
        assertEquals(0, player.resources[ResourceType.ORE])
        assertEquals(0, player.resources[ResourceType.GRAIN])
        assertEquals(0, player.resources[ResourceType.WOOL])
        assertEquals(1, player.newDevCards.size)
    }

    @Test
    fun `cannot play dev card on same turn it was bought`() {
        val state = setupMainPhaseState()
        state.turnPhase = TurnPhase.TRADE_BUILD
        val player = state.playerById("player0")!!

        // Give resources to buy
        player.resources[ResourceType.ORE] = 1
        player.resources[ResourceType.GRAIN] = 1
        player.resources[ResourceType.WOOL] = 1

        // Ensure deck has a knight at top
        state.devCardDeck.clear()
        state.devCardDeck.add(DevelopmentCardType.KNIGHT)

        engine.execute(state, GameAction.BuyDevelopmentCard("player0")).getOrThrow()

        // Card is in newDevCards, not devCards
        assertEquals(1, player.newDevCards.size)
        assertEquals(0, player.devCards.size)

        // Try to play it - should fail because it's not in devCards
        val result = engine.execute(state, GameAction.PlayKnight("player0"))
        assertTrue(result.isFailure)
    }

    @Test
    fun `knight moves robber and allows steal`() {
        val state = setupMainPhaseState()
        state.turnPhase = TurnPhase.TRADE_BUILD
        val player = state.playerById("player0")!!

        player.devCards.add(DevelopmentCardType.KNIGHT)

        val result = engine.execute(state, GameAction.PlayKnight("player0"))
        assertTrue(result.isSuccess)
        assertEquals(TurnPhase.ROBBER_MOVE, state.turnPhase)
        assertEquals(1, player.knightsPlayed)
    }

    @Test
    fun `road building places 2 roads free`() {
        val state = setupMainPhaseState()
        state.turnPhase = TurnPhase.TRADE_BUILD
        val player = state.playerById("player0")!!

        player.devCards.add(DevelopmentCardType.ROAD_BUILDING)
        player.resources.keys.forEach { player.resources[it] = 0 } // No resources

        engine.execute(state, GameAction.PlayRoadBuilding("player0")).getOrThrow()
        assertEquals(2, state.roadBuildingRoadsLeft)

        // Find a valid edge extending from player's existing network
        val playerBuilding = state.buildings.first { it.playerId == "player0" }
        val buildingEdges = HexUtils.edgesOfVertex(playerBuilding.vertex)
        val freeEdge1 = buildingEdges.first { state.roadAt(it) == null }

        engine.execute(state, GameAction.PlaceRoad("player0", freeEdge1)).getOrThrow()
        assertEquals(1, state.roadBuildingRoadsLeft)
        assertEquals(0, player.resources[ResourceType.BRICK]) // No cost

        // Place second free road extending from the first
        val e1Vertices = HexUtils.verticesOfEdge(freeEdge1)
        val extensionVertex = e1Vertices.first { it != playerBuilding.vertex }
        val adjEdges2 = HexUtils.edgesOfVertex(extensionVertex)
        val freeEdge2 = adjEdges2.first { state.roadAt(it) == null && it != freeEdge1 }

        engine.execute(state, GameAction.PlaceRoad("player0", freeEdge2)).getOrThrow()
        assertEquals(0, state.roadBuildingRoadsLeft)
    }

    @Test
    fun `year of plenty takes any 2 resources from bank`() {
        val state = setupMainPhaseState()
        state.turnPhase = TurnPhase.TRADE_BUILD
        val player = state.playerById("player0")!!

        player.devCards.add(DevelopmentCardType.YEAR_OF_PLENTY)
        val oreBefore = player.resources[ResourceType.ORE] ?: 0
        val grainBefore = player.resources[ResourceType.GRAIN] ?: 0

        val result = engine.execute(state, GameAction.PlayYearOfPlenty(
            "player0", ResourceType.ORE, ResourceType.GRAIN
        ))
        assertTrue(result.isSuccess)
        assertEquals(oreBefore + 1, player.resources[ResourceType.ORE])
        assertEquals(grainBefore + 1, player.resources[ResourceType.GRAIN])
    }

    @Test
    fun `monopoly takes all of named resource from other players`() {
        val state = setupMainPhaseState()
        state.turnPhase = TurnPhase.TRADE_BUILD
        val p0 = state.playerById("player0")!!
        val p1 = state.playerById("player1")!!

        p0.devCards.add(DevelopmentCardType.MONOPOLY)
        p1.resources[ResourceType.BRICK] = 5
        val p0BrickBefore = p0.resources[ResourceType.BRICK] ?: 0

        val result = engine.execute(state, GameAction.PlayMonopoly("player0", ResourceType.BRICK))
        assertTrue(result.isSuccess)
        assertEquals(0, p1.resources[ResourceType.BRICK])
        assertEquals(p0BrickBefore + 5, p0.resources[ResourceType.BRICK])
    }

    @Test
    fun `cannot play two dev cards in same turn`() {
        val state = setupMainPhaseState()
        state.turnPhase = TurnPhase.TRADE_BUILD
        val player = state.playerById("player0")!!

        player.devCards.add(DevelopmentCardType.YEAR_OF_PLENTY)
        player.devCards.add(DevelopmentCardType.MONOPOLY)

        engine.execute(state, GameAction.PlayYearOfPlenty(
            "player0", ResourceType.ORE, ResourceType.GRAIN
        )).getOrThrow()

        val result = engine.execute(state, GameAction.PlayMonopoly("player0", ResourceType.BRICK))
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("Already played") == true)
    }

    @Test
    fun `victory point cards add VP immediately`() {
        val state = setupMainPhaseState()
        state.turnPhase = TurnPhase.TRADE_BUILD
        val player = state.playerById("player0")!!

        val vpBefore = player.victoryPoints
        player.resources[ResourceType.ORE] = 1
        player.resources[ResourceType.GRAIN] = 1
        player.resources[ResourceType.WOOL] = 1

        state.devCardDeck.clear()
        state.devCardDeck.add(DevelopmentCardType.VICTORY_POINT)

        engine.execute(state, GameAction.BuyDevelopmentCard("player0")).getOrThrow()
        assertEquals(vpBefore + 1, player.victoryPoints)
    }
}
