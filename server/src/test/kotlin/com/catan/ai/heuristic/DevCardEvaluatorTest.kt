package com.catan.ai.heuristic

import com.catan.game.GameEngine
import com.catan.game.GameEngineTestHelper
import com.catan.model.*
import kotlin.test.Test
import kotlin.test.assertTrue

class DevCardEvaluatorTest {

    private val engine = GameEngine()

    @Test
    fun `buying dev card when close to largest army scores high`() {
        val state = GameEngineTestHelper.completeSetup(
            GameEngineTestHelper.createTestState(playerCount = 2),
            engine
        )
        val playerId = state.currentPlayer().id
        val player = state.playerById(playerId)!!
        player.knightsPlayed = 2 // One more knight = largest army

        val score = DevCardEvaluator.scoreBuy(state, playerId)
        assertTrue(score > 2.0, "Should be attractive to buy when close to largest army, got $score")
    }

    @Test
    fun `buying dev card less attractive when can afford city`() {
        val state = GameEngineTestHelper.completeSetup(
            GameEngineTestHelper.createTestState(playerCount = 2),
            engine
        )
        val playerId = state.currentPlayer().id
        val player = state.playerById(playerId)!!
        player.resources[ResourceType.GRAIN] = 2
        player.resources[ResourceType.ORE] = 3

        val scoreWithCity = DevCardEvaluator.scoreBuy(state, playerId)

        player.resources[ResourceType.GRAIN] = 0
        player.resources[ResourceType.ORE] = 0
        val scoreWithout = DevCardEvaluator.scoreBuy(state, playerId)

        assertTrue(scoreWithout >= scoreWithCity,
            "Should prefer city over dev card (without=$scoreWithout, with=$scoreWithCity)")
    }

    @Test
    fun `playing 3rd knight for largest army scores very high`() {
        val state = GameEngineTestHelper.completeSetup(
            GameEngineTestHelper.createTestState(playerCount = 2),
            engine
        )
        val playerId = state.currentPlayer().id
        val player = state.playerById(playerId)!!
        player.knightsPlayed = 2
        player.devCards.add(DevelopmentCardType.KNIGHT)

        val score = DevCardEvaluator.scorePlayKnight(state, playerId)
        assertTrue(score > 5.0, "Playing 3rd knight should score very high (largest army), got $score")
    }

    @Test
    fun `playing knight when robber is on own hex gets bonus`() {
        val state = GameEngineTestHelper.completeSetup(
            GameEngineTestHelper.createTestState(playerCount = 2),
            engine
        )
        val playerId = state.currentPlayer().id
        val player = state.playerById(playerId)!!
        player.devCards.add(DevelopmentCardType.KNIGHT)

        // Move robber to a hex where AI has a building
        val ownBuilding = state.buildings.first { it.playerId == playerId }
        val ownHex = com.catan.game.HexUtils.hexesOfVertex(ownBuilding.vertex).first()
        val newTiles = state.tiles.map {
            when {
                it.hasRobber -> it.copy(hasRobber = false)
                it.coord == ownHex -> it.copy(hasRobber = true)
                else -> it
            }
        }
        val stateWithRobber = state.copy(tiles = newTiles, robberLocation = ownHex)

        val scoreWithRobber = DevCardEvaluator.scorePlayKnight(stateWithRobber, playerId)

        // Compare with robber not on own hex
        val scoreWithout = DevCardEvaluator.scorePlayKnight(state, playerId)

        assertTrue(scoreWithRobber > scoreWithout,
            "Knight with robber on own hex ($scoreWithRobber) should score higher than without ($scoreWithout)")
    }

    @Test
    fun `road building scores high when close to longest road`() {
        val state = GameEngineTestHelper.completeSetup(
            GameEngineTestHelper.createTestState(playerCount = 2),
            engine
        )
        val playerId = state.currentPlayer().id

        // Add roads to get close to longest road
        val playerBuilding = state.buildings.first { it.playerId == playerId }
        var lastVertex = playerBuilding.vertex
        val occupiedEdges = state.roads.map { it.edge }.toMutableSet()

        repeat(2) {
            val edges = com.catan.game.HexUtils.edgesOfVertex(lastVertex)
                .filter { it !in occupiedEdges }
            if (edges.isNotEmpty()) {
                val edge = edges.first()
                state.roads.add(Road(edge, playerId))
                occupiedEdges.add(edge)
                val vertices = com.catan.game.HexUtils.verticesOfEdge(edge)
                lastVertex = if (vertices[0] == lastVertex) vertices[1] else vertices[0]
            }
        }

        val score = DevCardEvaluator.scorePlayRoadBuilding(state, playerId)
        assertTrue(score > 2.0, "Road building near longest road should score well, got $score")
    }

    @Test
    fun `monopoly on resource opponents have scores higher`() {
        val state = GameEngineTestHelper.completeSetup(
            GameEngineTestHelper.createTestState(playerCount = 2),
            engine
        )
        val playerId = state.currentPlayer().id
        val opponent = state.players.first { it.id != playerId }

        // Opponent has lots of ore
        opponent.resources[ResourceType.ORE] = 5
        opponent.resources[ResourceType.BRICK] = 0

        val oreScore = DevCardEvaluator.scorePlayMonopoly(state, playerId, ResourceType.ORE)
        val brickScore = DevCardEvaluator.scorePlayMonopoly(state, playerId, ResourceType.BRICK)

        assertTrue(oreScore > brickScore,
            "Monopoly on ore ($oreScore) should score higher than brick ($brickScore)")
    }

    @Test
    fun `year of plenty completing a city scores highest`() {
        val state = GameEngineTestHelper.completeSetup(
            GameEngineTestHelper.createTestState(playerCount = 2),
            engine
        )
        val playerId = state.currentPlayer().id
        val player = state.playerById(playerId)!!
        player.resources.replaceAll { _, _ -> 0 }
        player.resources[ResourceType.GRAIN] = 1
        player.resources[ResourceType.ORE] = 3

        // Taking grain+grain would complete a city (need 2 grain + 3 ore)
        // Note: the simulated player would have grain=2, ore=3 which actually doesn't
        // match city cost (grain=2, ore=3). Let's adjust:
        // Player has grain=1, ore=3. Taking grain+grain gives grain=3, ore=3. Has city resources.
        val cityScore = DevCardEvaluator.scorePlayYearOfPlenty(state, playerId, ResourceType.GRAIN, ResourceType.GRAIN)

        // Taking brick+wool doesn't complete anything useful
        val otherScore = DevCardEvaluator.scorePlayYearOfPlenty(state, playerId, ResourceType.BRICK, ResourceType.WOOL)

        assertTrue(cityScore > otherScore,
            "Year of plenty for city ($cityScore) should score higher than random ($otherScore)")
    }

    @Test
    fun `scores are finite`() {
        val state = GameEngineTestHelper.completeSetup(
            GameEngineTestHelper.createTestState(playerCount = 2),
            engine
        )
        val playerId = state.currentPlayer().id

        assertTrue(DevCardEvaluator.scoreBuy(state, playerId).isFinite())
        assertTrue(DevCardEvaluator.scorePlayKnight(state, playerId).isFinite())
        assertTrue(DevCardEvaluator.scorePlayRoadBuilding(state, playerId).isFinite())
        for (r in ResourceType.entries) {
            assertTrue(DevCardEvaluator.scorePlayMonopoly(state, playerId, r).isFinite())
        }
        for (r1 in ResourceType.entries) {
            for (r2 in ResourceType.entries) {
                assertTrue(DevCardEvaluator.scorePlayYearOfPlenty(state, playerId, r1, r2).isFinite())
            }
        }
    }
}
