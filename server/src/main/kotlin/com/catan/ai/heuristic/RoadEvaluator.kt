package com.catan.ai.heuristic

import com.catan.ai.DifficultyConfig
import com.catan.game.HexUtils
import com.catan.game.LongestRoadCalculator
import com.catan.model.*

object RoadEvaluator {

    private const val EXPANSION_WEIGHT = 2.0
    private const val LONGEST_ROAD_WEIGHT = 3.0
    private const val BLOCKING_WEIGHT = 1.0

    fun score(state: GameState, edge: EdgeCoord, playerId: String, config: DifficultyConfig? = null): Double {
        var score = 0.0

        score += expansionScore(state, edge, playerId) * EXPANSION_WEIGHT
        score += longestRoadScore(state, edge, playerId) * LONGEST_ROAD_WEIGHT

        if (config?.considerBlocking == true) {
            score += blockingScore(state, edge, playerId) * BLOCKING_WEIGHT
        }

        return score
    }

    private fun expansionScore(state: GameState, edge: EdgeCoord, playerId: String): Double {
        val vertices = HexUtils.verticesOfEdge(edge)
        var bestVertexScore = 0.0

        for (vertex in vertices) {
            // Check if this vertex is a potential settlement location
            val occupied = state.buildings.map { it.vertex }.toSet()
            val tooClose = occupied.flatMap { HexUtils.adjacentVertices(it) }.toSet()

            if (vertex !in occupied && vertex !in tooClose) {
                val vertexScore = VertexEvaluator.score(state, vertex, playerId)
                bestVertexScore = maxOf(bestVertexScore, vertexScore)
            }
        }

        return bestVertexScore
    }

    private fun longestRoadScore(state: GameState, edge: EdgeCoord, playerId: String): Double {
        // Calculate current longest road
        val currentLength = LongestRoadCalculator.calculate(playerId, state.roads, state.buildings)

        // Simulate adding this road
        val simulatedRoads = state.roads + Road(edge, playerId)
        val newLength = LongestRoadCalculator.calculate(playerId, simulatedRoads, state.buildings)

        val lengthGain = newLength - currentLength

        // Bonus if this reaches or extends past 5 (longest road threshold)
        return when {
            currentLength < 5 && newLength >= 5 -> 5.0 + lengthGain // Big bonus for claiming longest road
            newLength >= 5 && state.longestRoadHolder != playerId -> 3.0 + lengthGain
            else -> lengthGain.toDouble()
        }
    }

    private fun blockingScore(state: GameState, edge: EdgeCoord, playerId: String): Double {
        val vertices = HexUtils.verticesOfEdge(edge)
        var score = 0.0

        for (vertex in vertices) {
            // Check if opponents have roads leading toward this vertex
            val adjacentEdges = HexUtils.edgesOfVertex(vertex)
            val opponentRoadsNearby = adjacentEdges.count { adjEdge ->
                state.roads.any { it.edge == adjEdge && it.playerId != playerId }
            }
            if (opponentRoadsNearby > 0) {
                // Check if vertex is a good settlement spot
                val occupied = state.buildings.map { it.vertex }.toSet()
                val tooClose = occupied.flatMap { HexUtils.adjacentVertices(it) }.toSet()
                if (vertex !in occupied && vertex !in tooClose) {
                    score += 1.0
                }
            }
        }

        return score
    }
}
