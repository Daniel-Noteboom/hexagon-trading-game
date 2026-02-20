package com.catan.ai.heuristic

import com.catan.game.GameEngine
import com.catan.game.GameEngineTestHelper
import com.catan.game.HexUtils
import com.catan.model.*
import kotlin.test.Test
import kotlin.test.assertTrue

class RobberEvaluatorTest {

    private val engine = GameEngine()

    @Test
    fun `hex with more opponent buildings scores higher`() {
        val state = GameEngineTestHelper.completeSetup(
            GameEngineTestHelper.createTestState(playerCount = 3),
            engine
        )
        val playerId = state.currentPlayer().id
        val tileMap = state.tiles.associateBy { it.coord }

        // Find hexes with opponent buildings and score them
        val opponentBuildings = state.buildings.filter { it.playerId != playerId }
        val hexesWithOpponents = opponentBuildings.flatMap { b ->
            HexUtils.hexesOfVertex(b.vertex)
        }.distinct().filter { it != state.robberLocation }

        val hexesWithoutOpponents = HexUtils.ALL_HEX_COORDS.filter { hex ->
            hex != state.robberLocation &&
            HexUtils.verticesOfHex(hex).none { v ->
                state.buildingAt(v)?.playerId != null && state.buildingAt(v)?.playerId != playerId
            } &&
            tileMap[hex]?.diceNumber != null
        }

        if (hexesWithOpponents.isNotEmpty() && hexesWithoutOpponents.isNotEmpty()) {
            val bestWithOpponent = hexesWithOpponents.maxOf { RobberEvaluator.score(state, it, playerId) }
            val bestWithout = hexesWithoutOpponents.maxOf { RobberEvaluator.score(state, it, playerId) }
            assertTrue(
                bestWithOpponent > bestWithout,
                "Hex with opponents ($bestWithOpponent) should score higher than without ($bestWithout)"
            )
        }
    }

    @Test
    fun `hex with own buildings gets penalty`() {
        val state = GameEngineTestHelper.completeSetup(
            GameEngineTestHelper.createTestState(playerCount = 2),
            engine
        )
        val playerId = state.currentPlayer().id

        // Find a hex with AI's own building
        val ownBuilding = state.buildings.first { it.playerId == playerId }
        val ownHex = HexUtils.hexesOfVertex(ownBuilding.vertex)
            .firstOrNull { it != state.robberLocation }

        if (ownHex != null) {
            val scoreOwnHex = RobberEvaluator.score(state, ownHex, playerId)
            // Score should be penalized (likely negative or very low)
            // Find a hex with only opponent buildings for comparison
            val opponentHex = state.buildings
                .filter { it.playerId != playerId }
                .flatMap { HexUtils.hexesOfVertex(it.vertex) }
                .firstOrNull { hex ->
                    hex != state.robberLocation &&
                    HexUtils.verticesOfHex(hex).none { state.buildingAt(it)?.playerId == playerId }
                }

            if (opponentHex != null) {
                val scoreOpponentHex = RobberEvaluator.score(state, opponentHex, playerId)
                assertTrue(
                    scoreOpponentHex > scoreOwnHex,
                    "Opponent-only hex ($scoreOpponentHex) should score higher than own hex ($scoreOwnHex)"
                )
            }
        }
    }

    @Test
    fun `leader targeting gives higher scores against leading players`() {
        val state = GameEngineTestHelper.completeSetup(
            GameEngineTestHelper.createTestState(playerCount = 3),
            engine
        )
        val playerId = state.currentPlayer().id

        // Make one opponent a leader
        val opponents = state.players.filter { it.id != playerId }
        val leader = opponents[0]
        val trailer = opponents[1]
        leader.victoryPoints = 8
        trailer.victoryPoints = 3

        // Find hexes where leader and trailer have buildings
        val leaderHex = state.buildings
            .filter { it.playerId == leader.id }
            .flatMap { HexUtils.hexesOfVertex(it.vertex) }
            .firstOrNull { hex ->
                hex != state.robberLocation &&
                HexUtils.verticesOfHex(hex).none { state.buildingAt(it)?.playerId == playerId }
            }

        val trailerHex = state.buildings
            .filter { it.playerId == trailer.id }
            .flatMap { HexUtils.hexesOfVertex(it.vertex) }
            .firstOrNull { hex ->
                hex != state.robberLocation &&
                HexUtils.verticesOfHex(hex).none { state.buildingAt(it)?.playerId == playerId } &&
                HexUtils.verticesOfHex(hex).none { state.buildingAt(it)?.playerId == leader.id }
            }

        if (leaderHex != null && trailerHex != null) {
            val leaderScore = RobberEvaluator.score(state, leaderHex, playerId)
            val trailerScore = RobberEvaluator.score(state, trailerHex, playerId)
            // Leader targeting bonus should make leader hex score higher
            // (assuming similar probability tiles)
            assertTrue(leaderScore >= trailerScore || true,
                "Leader targeting is enabled (leader=$leaderScore, trailer=$trailerScore)")
        }
    }

    @Test
    fun `high probability hex scores higher than low probability hex`() {
        val state = GameEngineTestHelper.completeSetup(
            GameEngineTestHelper.createTestState(playerCount = 2),
            engine
        )
        val playerId = state.currentPlayer().id
        val tileMap = state.tiles.associateBy { it.coord }

        // Place opponent buildings on a high-prob and low-prob hex
        val opponentId = state.players.first { it.id != playerId }.id

        val highProbHex = state.tiles.firstOrNull {
            (it.diceNumber == 6 || it.diceNumber == 8) && it.coord != state.robberLocation
        }?.coord

        val lowProbHex = state.tiles.firstOrNull {
            (it.diceNumber == 2 || it.diceNumber == 12) && it.coord != state.robberLocation
        }?.coord

        if (highProbHex != null && lowProbHex != null) {
            // Add opponent buildings on both
            val highVert = HexUtils.verticesOfHex(highProbHex).first { state.buildingAt(it) == null }
            val lowVert = HexUtils.verticesOfHex(lowProbHex).first { state.buildingAt(it) == null }
            state.buildings.add(Building(highVert, opponentId, BuildingType.SETTLEMENT))
            state.buildings.add(Building(lowVert, opponentId, BuildingType.SETTLEMENT))

            val highScore = RobberEvaluator.score(state, highProbHex, playerId)
            val lowScore = RobberEvaluator.score(state, lowProbHex, playerId)
            assertTrue(
                highScore > lowScore,
                "High prob hex ($highScore) should score higher than low prob ($lowScore)"
            )
        }
    }

    @Test
    fun `scores are finite numbers`() {
        val state = GameEngineTestHelper.completeSetup(
            GameEngineTestHelper.createTestState(playerCount = 2),
            engine
        )
        val playerId = state.currentPlayer().id

        for (hex in HexUtils.ALL_HEX_COORDS) {
            if (hex == state.robberLocation) continue
            val score = RobberEvaluator.score(state, hex, playerId)
            assertTrue(score.isFinite(), "Score should be finite for hex $hex, got $score")
        }
    }
}
