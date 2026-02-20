package com.catan.game

import com.catan.model.*
import kotlin.test.*

class HexUtilsTest {

    @Test
    fun `ALL_HEX_COORDS contains exactly 19 hexes`() {
        assertEquals(19, HexUtils.ALL_HEX_COORDS.size)
    }

    @Test
    fun `ALL_HEX_COORDS contains center and all radius-2 hexes`() {
        assertContains(HexUtils.ALL_HEX_COORDS, HexCoord(0, 0))
        assertContains(HexUtils.ALL_HEX_COORDS, HexCoord(2, -2))
        assertContains(HexUtils.ALL_HEX_COORDS, HexCoord(-2, 2))
        // Outside the board
        assertFalse(HexCoord(3, 0) in HexUtils.ALL_HEX_COORDS)
        assertFalse(HexCoord(2, 1) in HexUtils.ALL_HEX_COORDS)
    }

    @Test
    fun `hexNeighbors returns 6 for center tile`() {
        val neighbors = HexUtils.hexNeighbors(HexCoord(0, 0))
        assertEquals(6, neighbors.size)
    }

    @Test
    fun `hexNeighbors returns fewer for edge tiles`() {
        val neighbors = HexUtils.hexNeighbors(HexCoord(2, -2))
        assertTrue(neighbors.size < 6)
        assertTrue(neighbors.size >= 2)
    }

    @Test
    fun `hexNeighbors symmetry - if A is neighbor of B then B is neighbor of A`() {
        for (hex in HexUtils.ALL_HEX_COORDS) {
            for (neighbor in HexUtils.hexNeighbors(hex)) {
                assertTrue(
                    hex in HexUtils.hexNeighbors(neighbor),
                    "If $hex is neighbor of $neighbor, then $neighbor should list $hex as neighbor"
                )
            }
        }
    }

    @Test
    fun `verticesOfHex returns 6 vertices per hex`() {
        for (hex in HexUtils.ALL_HEX_COORDS) {
            val vertices = HexUtils.verticesOfHex(hex)
            assertEquals(6, vertices.size, "Hex $hex should have 6 vertices")
            assertEquals(6, vertices.toSet().size, "Hex $hex vertices should be unique")
        }
    }

    @Test
    fun `ALL_VERTICES contains exactly 54 vertices`() {
        assertEquals(54, HexUtils.ALL_VERTICES.size)
    }

    @Test
    fun `edgesOfHex returns 6 edges per hex`() {
        for (hex in HexUtils.ALL_HEX_COORDS) {
            val edges = HexUtils.edgesOfHex(hex)
            assertEquals(6, edges.size, "Hex $hex should have 6 edges")
            assertEquals(6, edges.toSet().size, "Hex $hex edges should be unique")
        }
    }

    @Test
    fun `ALL_EDGES contains exactly 72 edges`() {
        assertEquals(72, HexUtils.ALL_EDGES.size)
    }

    @Test
    fun `hexesOfVertex returns 1-3 hexes`() {
        for (vertex in HexUtils.ALL_VERTICES) {
            val hexes = HexUtils.hexesOfVertex(vertex)
            assertTrue(hexes.isNotEmpty(), "Vertex $vertex should touch at least 1 hex")
            assertTrue(hexes.size <= 3, "Vertex $vertex should touch at most 3 hexes")
        }
    }

    @Test
    fun `hexesOfVertex returns 3 for interior vertices`() {
        // The center hex's own N and S vertices should each touch 3 hexes
        val nVertex = VertexCoord(0, 0, VertexDirection.N)
        assertEquals(3, HexUtils.hexesOfVertex(nVertex).size)
        val sVertex = VertexCoord(0, 0, VertexDirection.S)
        assertEquals(3, HexUtils.hexesOfVertex(sVertex).size)
    }

    @Test
    fun `hexesOfVertex inverse of verticesOfHex`() {
        // For every hex, each of its vertices should list that hex in hexesOfVertex
        for (hex in HexUtils.ALL_HEX_COORDS) {
            for (vertex in HexUtils.verticesOfHex(hex)) {
                assertTrue(
                    hex in HexUtils.hexesOfVertex(vertex),
                    "Hex $hex should be in hexesOfVertex($vertex)"
                )
            }
        }
    }

    @Test
    fun `adjacentVertices returns 2-3 vertices`() {
        for (vertex in HexUtils.ALL_VERTICES) {
            val adj = HexUtils.adjacentVertices(vertex)
            assertTrue(adj.size in 2..3, "Vertex $vertex should have 2-3 adjacent vertices, got ${adj.size}")
        }
    }

    @Test
    fun `adjacentVertices symmetry`() {
        for (vertex in HexUtils.ALL_VERTICES) {
            for (adj in HexUtils.adjacentVertices(vertex)) {
                assertTrue(
                    vertex in HexUtils.adjacentVertices(adj),
                    "If $vertex is adjacent to $adj, then $adj should be adjacent to $vertex"
                )
            }
        }
    }

    @Test
    fun `edgesOfVertex returns 2-3 edges`() {
        for (vertex in HexUtils.ALL_VERTICES) {
            val edges = HexUtils.edgesOfVertex(vertex)
            assertTrue(edges.size in 2..3, "Vertex $vertex should have 2-3 edges, got ${edges.size}")
        }
    }

    @Test
    fun `verticesOfEdge returns exactly 2 vertices`() {
        for (edge in HexUtils.ALL_EDGES) {
            val vertices = HexUtils.verticesOfEdge(edge)
            assertEquals(2, vertices.size, "Edge $edge should have exactly 2 vertices")
            assertTrue(vertices[0] in HexUtils.ALL_VERTICES, "First vertex of $edge should be valid")
            assertTrue(vertices[1] in HexUtils.ALL_VERTICES, "Second vertex of $edge should be valid")
        }
    }

    @Test
    fun `verticesOfEdge inverse of edgesOfVertex`() {
        for (edge in HexUtils.ALL_EDGES) {
            for (vertex in HexUtils.verticesOfEdge(edge)) {
                assertTrue(
                    edge in HexUtils.edgesOfVertex(vertex),
                    "Edge $edge should be in edgesOfVertex($vertex)"
                )
            }
        }
    }

    @Test
    fun `hexesOfEdge returns 1-2 hexes`() {
        for (edge in HexUtils.ALL_EDGES) {
            val hexes = HexUtils.hexesOfEdge(edge)
            assertTrue(hexes.isNotEmpty(), "Edge $edge should touch at least 1 hex")
            assertTrue(hexes.size <= 2, "Edge $edge should touch at most 2 hexes")
        }
    }

    @Test
    fun `N vertices are only adjacent to S vertices and vice versa`() {
        for (vertex in HexUtils.ALL_VERTICES) {
            for (adj in HexUtils.adjacentVertices(vertex)) {
                assertNotEquals(
                    vertex.dir, adj.dir,
                    "Vertex $vertex and adjacent $adj should have different directions"
                )
            }
        }
    }

    @Test
    fun `coastal vertices have fewer than 3 adjacent hexes`() {
        val coastal = HexUtils.coastalVertices()
        assertTrue(coastal.isNotEmpty())
        for (v in coastal) {
            assertTrue(HexUtils.hexesOfVertex(v).size < 3)
        }
    }

    @Test
    fun `coastal edges have exactly 1 adjacent hex`() {
        val coastal = HexUtils.coastalEdges()
        assertTrue(coastal.isNotEmpty())
        for (e in coastal) {
            assertEquals(1, HexUtils.hexesOfEdge(e).size)
        }
    }
}
