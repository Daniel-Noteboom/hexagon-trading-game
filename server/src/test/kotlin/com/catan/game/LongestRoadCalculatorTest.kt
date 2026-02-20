package com.catan.game

import com.catan.model.*
import kotlin.test.*

class LongestRoadCalculatorTest {

    @Test
    fun `no roads returns 0`() {
        val result = LongestRoadCalculator.calculate("player1", emptyList(), emptyList())
        assertEquals(0, result)
    }

    @Test
    fun `simple chain of 3 roads returns 3`() {
        // Build a chain: v0 -e0- v1 -e1- v2 -e2- v3
        val v0 = VertexCoord(0, 0, VertexDirection.S)
        val e0 = HexUtils.edgesOfVertex(v0).first()
        val v1 = HexUtils.verticesOfEdge(e0).first { it != v0 }
        val e1 = HexUtils.edgesOfVertex(v1).first { it != e0 }
        val v2 = HexUtils.verticesOfEdge(e1).first { it != v1 }
        val e2 = HexUtils.edgesOfVertex(v2).first { it != e1 }

        val roads = listOf(
            Road(e0, "player1"),
            Road(e1, "player1"),
            Road(e2, "player1")
        )

        val result = LongestRoadCalculator.calculate("player1", roads, emptyList())
        assertEquals(3, result)
    }

    @Test
    fun `branching roads returns longest branch`() {
        // Build roads from a central vertex in different directions
        val center = VertexCoord(0, 0, VertexDirection.S)
        val edges = HexUtils.edgesOfVertex(center)
        assertTrue(edges.size >= 2)

        // Branch 1: 2 roads
        val e0 = edges[0]
        val v1 = HexUtils.verticesOfEdge(e0).first { it != center }
        val nextEdges1 = HexUtils.edgesOfVertex(v1).filter { it != e0 }
        val e1 = nextEdges1.first()

        // Branch 2: 1 road
        val e2 = edges[1]

        val roads = listOf(
            Road(e0, "player1"),
            Road(e1, "player1"),
            Road(e2, "player1")
        )

        val result = LongestRoadCalculator.calculate("player1", roads, emptyList())
        // Longest branch is e0 + e1 = 2, or e2 + e0 + e1 = 3 (through center)
        assertEquals(3, result)
    }

    @Test
    fun `opponent building breaks chain`() {
        val v0 = VertexCoord(0, 0, VertexDirection.S)
        val e0 = HexUtils.edgesOfVertex(v0).first()
        val v1 = HexUtils.verticesOfEdge(e0).first { it != v0 }
        val e1 = HexUtils.edgesOfVertex(v1).first { it != e0 }
        val v2 = HexUtils.verticesOfEdge(e1).first { it != v1 }
        val e2 = HexUtils.edgesOfVertex(v2).first { it != e1 }

        val roads = listOf(
            Road(e0, "player1"),
            Road(e1, "player1"),
            Road(e2, "player1")
        )

        // Place opponent building on v1 (middle of the chain)
        val buildings = listOf(Building(v1, "player2", BuildingType.SETTLEMENT))

        val result = LongestRoadCalculator.calculate("player1", roads, buildings)
        // Chain is broken at v1: max is 1 (either e0 alone or e1+e2=2 from the other side)
        assertTrue(result <= 2, "Chain should be broken by opponent building, got $result")
    }

    @Test
    fun `own building does NOT break chain`() {
        val v0 = VertexCoord(0, 0, VertexDirection.S)
        val e0 = HexUtils.edgesOfVertex(v0).first()
        val v1 = HexUtils.verticesOfEdge(e0).first { it != v0 }
        val e1 = HexUtils.edgesOfVertex(v1).first { it != e0 }

        val roads = listOf(
            Road(e0, "player1"),
            Road(e1, "player1")
        )

        // Own building on v1
        val buildings = listOf(Building(v1, "player1", BuildingType.SETTLEMENT))

        val result = LongestRoadCalculator.calculate("player1", roads, buildings)
        assertEquals(2, result)
    }

    @Test
    fun `single road returns 1`() {
        val edge = HexUtils.ALL_EDGES.first()
        val roads = listOf(Road(edge, "player1"))

        val result = LongestRoadCalculator.calculate("player1", roads, emptyList())
        assertEquals(1, result)
    }

    @Test
    fun `only counts roads of specified player`() {
        val edges = HexUtils.ALL_EDGES.toList()
        val roads = listOf(
            Road(edges[0], "player1"),
            Road(edges[1], "player2"),
            Road(edges[2], "player1")
        )

        // Player2 has only 1 road
        val result = LongestRoadCalculator.calculate("player2", roads, emptyList())
        assertEquals(1, result)
    }
}
