package com.catan.game

import com.catan.model.*

object LongestRoadCalculator {

    /**
     * Calculates the length of the longest contiguous road chain for a player.
     * An opponent's settlement/city on a vertex breaks the road chain at that point.
     */
    fun calculate(playerId: String, roads: List<Road>, buildings: List<Building>): Int {
        val playerRoads = roads.filter { it.playerId == playerId }
        if (playerRoads.isEmpty()) return 0

        // Build adjacency: vertex → list of edges belonging to this player
        val vertexToEdges = mutableMapOf<VertexCoord, MutableList<EdgeCoord>>()
        for (road in playerRoads) {
            for (v in HexUtils.verticesOfEdge(road.edge)) {
                vertexToEdges.getOrPut(v) { mutableListOf() }.add(road.edge)
            }
        }

        // Vertices occupied by opponents break the chain
        val blockedVertices = buildings
            .filter { it.playerId != playerId }
            .map { it.vertex }
            .toSet()

        val playerEdgeSet = playerRoads.map { it.edge }.toSet()
        var longest = 0

        // Try DFS from every vertex that has player roads
        for (startVertex in vertexToEdges.keys) {
            val visited = mutableSetOf<EdgeCoord>()
            val length = dfs(startVertex, visited, vertexToEdges, blockedVertices, playerEdgeSet)
            longest = maxOf(longest, length)
        }

        return longest
    }

    private fun dfs(
        vertex: VertexCoord,
        visited: MutableSet<EdgeCoord>,
        vertexToEdges: Map<VertexCoord, List<EdgeCoord>>,
        blockedVertices: Set<VertexCoord>,
        playerEdges: Set<EdgeCoord>
    ): Int {
        val edges = vertexToEdges[vertex] ?: return 0
        var maxLength = 0

        for (edge in edges) {
            if (edge in visited || edge !in playerEdges) continue

            visited.add(edge)

            val edgeVertices = HexUtils.verticesOfEdge(edge)
            val otherVertex = if (edgeVertices[0] == vertex) edgeVertices[1] else edgeVertices[0]

            var length = 1
            if (otherVertex !in blockedVertices) {
                length += dfs(otherVertex, visited, vertexToEdges, blockedVertices, playerEdges)
            }
            maxLength = maxOf(maxLength, length)

            visited.remove(edge)
        }

        return maxLength
    }
}
