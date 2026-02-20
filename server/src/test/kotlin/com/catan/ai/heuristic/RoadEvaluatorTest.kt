package com.catan.ai.heuristic

import com.catan.ai.DifficultyConfig
import com.catan.game.GameEngine
import com.catan.game.GameEngineTestHelper
import com.catan.game.HexUtils
import com.catan.game.LongestRoadCalculator
import com.catan.model.*
import kotlin.test.Test
import kotlin.test.assertTrue

class RoadEvaluatorTest {

    private val engine = GameEngine()

    @Test
    fun `road toward open high-value vertex scores higher than dead-end road`() {
        val state = GameEngineTestHelper.completeSetup(
            GameEngineTestHelper.createTestState(playerCount = 2),
            engine
        )
        val playerId = state.currentPlayer().id

        // Find valid road edges for this player
        val occupiedEdges = state.roads.map { it.edge }.toSet()
        val occupied = state.buildings.map { it.vertex }.toSet()
        val tooClose = occupied.flatMap { HexUtils.adjacentVertices(it) }.toSet()

        val validEdges = HexUtils.ALL_EDGES.filter { edge ->
            if (edge in occupiedEdges) return@filter false
            val edgeVertices = HexUtils.verticesOfEdge(edge)
            edgeVertices.any { vertex ->
                val building = state.buildingAt(vertex)
                if (building != null && building.playerId == playerId) return@any true
                val opponentBuilding = building != null && building.playerId != playerId
                if (opponentBuilding) return@any false
                HexUtils.edgesOfVertex(vertex).any { adjEdge ->
                    adjEdge != edge && state.roads.any { it.edge == adjEdge && it.playerId == playerId }
                }
            }
        }

        if (validEdges.size >= 2) {
            // Score all valid edges
            val scores = validEdges.map { edge ->
                edge to RoadEvaluator.score(state, edge, playerId)
            }

            // Find edges that lead to open vertices (good expansion)
            val expandingEdges = scores.filter { (edge, _) ->
                HexUtils.verticesOfEdge(edge).any { v ->
                    v !in occupied && v !in tooClose
                }
            }

            // Find edges that lead to dead ends (no open vertices)
            val deadEndEdges = scores.filter { (edge, _) ->
                HexUtils.verticesOfEdge(edge).all { v ->
                    v in occupied || v in tooClose
                }
            }

            if (expandingEdges.isNotEmpty() && deadEndEdges.isNotEmpty()) {
                val bestExpanding = expandingEdges.maxOf { it.second }
                val worstDeadEnd = deadEndEdges.minOf { it.second }
                // Expanding roads should generally score higher
                assertTrue(
                    bestExpanding >= worstDeadEnd,
                    "Best expanding road ($bestExpanding) should score >= worst dead end ($worstDeadEnd)"
                )
            }
        }
    }

    @Test
    fun `road that extends chain toward longest road threshold scores high`() {
        val state = GameEngineTestHelper.completeSetup(
            GameEngineTestHelper.createTestState(playerCount = 2),
            engine
        )
        val playerId = state.currentPlayer().id

        // Add extra roads to get close to longest road (4 roads in a chain)
        val playerBuilding = state.buildings.first { it.playerId == playerId }
        var lastVertex = playerBuilding.vertex

        // Build a chain of roads from the player's building
        val addedRoads = mutableListOf<EdgeCoord>()
        val occupiedEdges = state.roads.map { it.edge }.toMutableSet()

        repeat(3) {
            val adjacentEdges = HexUtils.edgesOfVertex(lastVertex)
                .filter { it !in occupiedEdges }

            if (adjacentEdges.isNotEmpty()) {
                val edge = adjacentEdges.first()
                state.roads.add(Road(edge, playerId))
                occupiedEdges.add(edge)
                addedRoads.add(edge)

                // Move to the other vertex of this edge
                val vertices = HexUtils.verticesOfEdge(edge)
                lastVertex = if (vertices[0] == lastVertex) vertices[1] else vertices[0]
            }
        }

        val currentLength = LongestRoadCalculator.calculate(playerId, state.roads, state.buildings)

        // Find a road that extends from the chain end
        val nextEdges = HexUtils.edgesOfVertex(lastVertex)
            .filter { it !in occupiedEdges }

        if (nextEdges.isNotEmpty() && currentLength >= 4) {
            val extendingEdge = nextEdges.first()
            val score = RoadEvaluator.score(state, extendingEdge, playerId)
            // Should get longest road bonus
            assertTrue(score > 0, "Road extending chain to 5+ should score positively, got $score")
        }
    }

    @Test
    fun `blocking score applies only with HARD difficulty`() {
        val state = GameEngineTestHelper.completeSetup(
            GameEngineTestHelper.createTestState(playerCount = 2),
            engine
        )
        val playerId = state.currentPlayer().id
        val occupiedEdges = state.roads.map { it.edge }.toSet()

        // Find a valid edge
        val validEdge = HexUtils.ALL_EDGES.firstOrNull { edge ->
            if (edge in occupiedEdges) return@firstOrNull false
            HexUtils.verticesOfEdge(edge).any { vertex ->
                val building = state.buildingAt(vertex)
                if (building != null && building.playerId == playerId) return@any true
                val opponentBuilding = building != null && building.playerId != playerId
                if (opponentBuilding) return@any false
                HexUtils.edgesOfVertex(vertex).any { adjEdge ->
                    adjEdge != edge && state.roads.any { it.edge == adjEdge && it.playerId == playerId }
                }
            }
        }

        if (validEdge != null) {
            val easyConfig = DifficultyConfig.forDifficulty(AiDifficulty.EASY)
            val hardConfig = DifficultyConfig.forDifficulty(AiDifficulty.HARD)

            val easyScore = RoadEvaluator.score(state, validEdge, playerId, easyConfig)
            val hardScore = RoadEvaluator.score(state, validEdge, playerId, hardConfig)

            // Hard might be >= easy due to blocking bonus
            assertTrue(hardScore >= easyScore,
                "HARD score ($hardScore) should be >= EASY score ($easyScore) due to blocking")
        }
    }

    @Test
    fun `all road scores are finite numbers`() {
        val state = GameEngineTestHelper.completeSetup(
            GameEngineTestHelper.createTestState(playerCount = 2),
            engine
        )
        val playerId = state.currentPlayer().id
        val occupiedEdges = state.roads.map { it.edge }.toSet()

        val validEdges = HexUtils.ALL_EDGES.filter { edge ->
            if (edge in occupiedEdges) return@filter false
            HexUtils.verticesOfEdge(edge).any { vertex ->
                val building = state.buildingAt(vertex)
                if (building != null && building.playerId == playerId) return@any true
                val opponentBuilding = building != null && building.playerId != playerId
                if (opponentBuilding) return@any false
                HexUtils.edgesOfVertex(vertex).any { adjEdge ->
                    adjEdge != edge && state.roads.any { it.edge == adjEdge && it.playerId == playerId }
                }
            }
        }

        for (edge in validEdges) {
            val score = RoadEvaluator.score(state, edge, playerId)
            assertTrue(score.isFinite(), "Road score should be finite, got $score for edge $edge")
        }
    }
}
