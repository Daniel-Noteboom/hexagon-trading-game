package com.catan.game

import com.catan.model.*

/**
 * Hex grid math for flat-top hexagons using axial coordinates (q, r).
 *
 * Vertex convention:
 *   Each vertex is canonically (q, r, N) or (q, r, S).
 *   N(q,r) = "left" vertex of hex (q,r), shared by hexes (q,r), (q-1,r), (q-1,r+1).
 *   S(q,r) = "right" vertex of hex (q,r), shared by hexes (q,r), (q+1,r-1), (q+1,r).
 *
 * Edge convention:
 *   Each edge is canonically (q, r, NE/E/SE).
 *   NE(q,r) = top-right edge of hex (q,r), connecting N(q+1,r-1) ↔ S(q,r).
 *   E(q,r)  = bottom-right edge of hex (q,r), connecting S(q,r) ↔ N(q+1,r).
 *   SE(q,r) = bottom edge of hex (q,r), connecting N(q+1,r) ↔ S(q-1,r+1).
 */
object HexUtils {

    /** All 19 hex coordinates for a standard Catan board (radius 2). */
    val ALL_HEX_COORDS: List<HexCoord> = buildList {
        for (q in -2..2) {
            for (r in -2..2) {
                if (kotlin.math.abs(q + r) <= 2) {
                    add(HexCoord(q, r))
                }
            }
        }
    }

    /** The 6 axial direction vectors for hex neighbors. */
    private val HEX_DIRECTIONS = listOf(
        HexCoord(1, -1), HexCoord(1, 0), HexCoord(0, 1),
        HexCoord(-1, 1), HexCoord(-1, 0), HexCoord(0, -1)
    )

    /** Returns the neighboring hex coordinates (only those within the board). */
    fun hexNeighbors(hex: HexCoord): List<HexCoord> =
        HEX_DIRECTIONS.map { HexCoord(hex.q + it.q, hex.r + it.r) }
            .filter { it in ALL_HEX_COORDS }

    /**
     * Returns the 6 vertices of a hex tile in canonical form.
     *
     * For flat-top hex (q,r), going clockwise from left:
     *   left        = N(q, r)
     *   upper-left  = S(q-1, r)
     *   upper-right = N(q+1, r-1)
     *   right       = S(q, r)
     *   lower-right = N(q+1, r)
     *   lower-left  = S(q-1, r+1)
     */
    fun verticesOfHex(hex: HexCoord): List<VertexCoord> {
        val (q, r) = hex
        return listOf(
            VertexCoord(q, r, VertexDirection.N),
            VertexCoord(q - 1, r, VertexDirection.S),
            VertexCoord(q + 1, r - 1, VertexDirection.N),
            VertexCoord(q, r, VertexDirection.S),
            VertexCoord(q + 1, r, VertexDirection.N),
            VertexCoord(q - 1, r + 1, VertexDirection.S)
        )
    }

    /**
     * Returns the 6 edges of a hex tile in canonical form.
     *
     * Owned edges (top-right, bottom-right, bottom):
     *   NE(q, r), E(q, r), SE(q, r)
     * Neighbor edges (bottom-left, top-left, top):
     *   NE(q-1, r+1), E(q-1, r), SE(q, r-1)
     */
    fun edgesOfHex(hex: HexCoord): List<EdgeCoord> {
        val (q, r) = hex
        return listOf(
            EdgeCoord(q, r, EdgeDirection.NE),
            EdgeCoord(q, r, EdgeDirection.E),
            EdgeCoord(q, r, EdgeDirection.SE),
            EdgeCoord(q - 1, r + 1, EdgeDirection.NE),
            EdgeCoord(q - 1, r, EdgeDirection.E),
            EdgeCoord(q, r - 1, EdgeDirection.SE)
        )
    }

    /**
     * Returns the 1-3 hexes that touch a given vertex (filtered to board hexes).
     */
    fun hexesOfVertex(vertex: VertexCoord): List<HexCoord> {
        val (q, r, dir) = vertex
        val candidates = when (dir) {
            VertexDirection.N -> listOf(HexCoord(q, r), HexCoord(q - 1, r), HexCoord(q - 1, r + 1))
            VertexDirection.S -> listOf(HexCoord(q, r), HexCoord(q + 1, r - 1), HexCoord(q + 1, r))
        }
        return candidates.filter { it in ALL_HEX_COORDS }
    }

    /**
     * Returns the 2-3 edges connected to a vertex (filtered to valid board edges).
     */
    fun edgesOfVertex(vertex: VertexCoord): List<EdgeCoord> {
        val (q, r, dir) = vertex
        val candidates = when (dir) {
            VertexDirection.N -> listOf(
                EdgeCoord(q - 1, r, EdgeDirection.E),
                EdgeCoord(q - 1, r + 1, EdgeDirection.NE),
                EdgeCoord(q - 1, r, EdgeDirection.SE)
            )
            VertexDirection.S -> listOf(
                EdgeCoord(q, r, EdgeDirection.NE),
                EdgeCoord(q, r, EdgeDirection.E),
                EdgeCoord(q + 1, r - 1, EdgeDirection.SE)
            )
        }
        return candidates.filter { it in ALL_EDGES }
    }

    /**
     * Returns the 2-3 vertices adjacent to a vertex (filtered to valid board vertices).
     */
    fun adjacentVertices(vertex: VertexCoord): List<VertexCoord> {
        val (q, r, dir) = vertex
        val candidates = when (dir) {
            VertexDirection.N -> listOf(
                VertexCoord(q - 1, r, VertexDirection.S),
                VertexCoord(q - 1, r + 1, VertexDirection.S),
                VertexCoord(q - 2, r + 1, VertexDirection.S)
            )
            VertexDirection.S -> listOf(
                VertexCoord(q + 1, r - 1, VertexDirection.N),
                VertexCoord(q + 1, r, VertexDirection.N),
                VertexCoord(q + 2, r - 1, VertexDirection.N)
            )
        }
        return candidates.filter { it in ALL_VERTICES }
    }

    /**
     * Returns the 2 vertices at either end of an edge.
     */
    fun verticesOfEdge(edge: EdgeCoord): List<VertexCoord> {
        val (q, r, dir) = edge
        return when (dir) {
            EdgeDirection.NE -> listOf(
                VertexCoord(q + 1, r - 1, VertexDirection.N),
                VertexCoord(q, r, VertexDirection.S)
            )
            EdgeDirection.E -> listOf(
                VertexCoord(q, r, VertexDirection.S),
                VertexCoord(q + 1, r, VertexDirection.N)
            )
            EdgeDirection.SE -> listOf(
                VertexCoord(q + 1, r, VertexDirection.N),
                VertexCoord(q - 1, r + 1, VertexDirection.S)
            )
        }
    }

    /**
     * Returns the 1-2 hexes on either side of an edge (filtered to board hexes).
     */
    fun hexesOfEdge(edge: EdgeCoord): List<HexCoord> {
        val (q, r, dir) = edge
        val candidates = when (dir) {
            EdgeDirection.NE -> listOf(HexCoord(q, r), HexCoord(q + 1, r - 1))
            EdgeDirection.E -> listOf(HexCoord(q, r), HexCoord(q + 1, r))
            EdgeDirection.SE -> listOf(HexCoord(q, r), HexCoord(q, r + 1))
        }
        return candidates.filter { it in ALL_HEX_COORDS }
    }

    /** All unique vertices on the board (54 total). */
    val ALL_VERTICES: Set<VertexCoord> by lazy {
        ALL_HEX_COORDS.flatMap { verticesOfHex(it) }.toSet()
    }

    /** All unique edges on the board (72 total). */
    val ALL_EDGES: Set<EdgeCoord> by lazy {
        ALL_HEX_COORDS.flatMap { edgesOfHex(it) }.toSet()
    }

    /**
     * Returns the coastal vertices (vertices touching fewer than 3 board hexes).
     */
    fun coastalVertices(): Set<VertexCoord> =
        ALL_VERTICES.filter { hexesOfVertex(it).size < 3 }.toSet()

    /**
     * Returns the coastal edges (edges with only 1 adjacent board hex).
     */
    fun coastalEdges(): Set<EdgeCoord> =
        ALL_EDGES.filter { hexesOfEdge(it).size == 1 }.toSet()
}
