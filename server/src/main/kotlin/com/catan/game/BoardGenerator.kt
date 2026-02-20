package com.catan.game

import com.catan.model.*

object BoardGenerator {

    /** Standard Catan tile distribution: 19 tiles total. */
    private val TILE_TYPES = listOf(
        TileType.FIELDS, TileType.FIELDS, TileType.FIELDS, TileType.FIELDS,
        TileType.PASTURE, TileType.PASTURE, TileType.PASTURE, TileType.PASTURE,
        TileType.FOREST, TileType.FOREST, TileType.FOREST, TileType.FOREST,
        TileType.HILLS, TileType.HILLS, TileType.HILLS,
        TileType.MOUNTAINS, TileType.MOUNTAINS, TileType.MOUNTAINS,
        TileType.DESERT
    )

    /**
     * Standard dice number distribution: 18 number tokens (desert gets none).
     * One each of 2 and 12, two each of 3-6 and 8-11.
     */
    private val DICE_NUMBERS = listOf(
        2, 3, 3, 4, 4, 5, 5, 6, 6,
        8, 8, 9, 9, 10, 10, 11, 11, 12
    )

    /** Standard port distribution: 9 ports. */
    private val PORT_TYPES = listOf(
        PortType.GENERIC_3_1, PortType.GENERIC_3_1, PortType.GENERIC_3_1, PortType.GENERIC_3_1,
        PortType.BRICK_2_1, PortType.LUMBER_2_1, PortType.ORE_2_1, PortType.GRAIN_2_1, PortType.WOOL_2_1
    )

    /**
     * Coastal edge pairs for port placement. Each port occupies one coastal edge
     * (identified by its two vertices). These are 9 fixed positions around the board.
     */
    private val PORT_EDGE_POSITIONS: List<EdgeCoord> by lazy {
        val coastal = HexUtils.coastalEdges().toList()
        // Pick 9 well-spaced coastal edges for ports.
        // We use a deterministic set of edges that are roughly evenly spaced.
        selectPortEdges(coastal)
    }

    private fun selectPortEdges(coastalEdges: List<EdgeCoord>): List<EdgeCoord> {
        // Group coastal edges by which hex they touch, then pick one edge per "segment"
        // For a standard board, we want 9 ports spread around the coast.
        // We'll use a fixed set of port positions (one per coastal hex face).
        // The coastal hexes in order around the perimeter:
        val perimeterHexes = listOf(
            HexCoord(0, -2), HexCoord(1, -2), HexCoord(2, -2),
            HexCoord(2, -1), HexCoord(2, 0),
            HexCoord(1, 1), HexCoord(0, 2),
            HexCoord(-1, 2), HexCoord(-2, 2),
            HexCoord(-2, 1), HexCoord(-2, 0),
            HexCoord(-1, -1)
        )

        // For each perimeter hex, find a coastal edge. We pick edges at roughly every
        // other hex position plus one extra, giving 9 ports for 12 perimeter positions.
        val portHexIndices = listOf(0, 1, 3, 4, 6, 7, 9, 10, 11)
        return portHexIndices.map { idx ->
            val hex = perimeterHexes[idx]
            val hexEdges = HexUtils.edgesOfHex(hex)
            hexEdges.first { it in coastalEdges }
        }
    }

    /**
     * Generates a random valid Catan board.
     */
    fun generateBoard(): Pair<List<HexTile>, List<Port>> {
        val tiles = generateTiles()
        val ports = generatePorts()
        return Pair(tiles, ports)
    }

    private fun generateTiles(): List<HexTile> {
        val coords = HexUtils.ALL_HEX_COORDS.toMutableList()
        var shuffledTypes: List<TileType>
        var shuffledNumbers: List<Int>
        var tiles: List<HexTile>

        // Keep shuffling until we get a valid board (no adjacent 6/8).
        do {
            shuffledTypes = TILE_TYPES.shuffled()
            shuffledNumbers = DICE_NUMBERS.shuffled()

            var numberIdx = 0
            tiles = coords.zip(shuffledTypes).map { (coord, tileType) ->
                if (tileType == TileType.DESERT) {
                    HexTile(coord, tileType, diceNumber = null, hasRobber = true)
                } else {
                    HexTile(coord, tileType, diceNumber = shuffledNumbers[numberIdx++], hasRobber = false)
                }
            }
        } while (!isValidNumberPlacement(tiles))

        return tiles
    }

    /**
     * Validates that no two adjacent hexes both have "red numbers" (6 or 8).
     */
    private fun isValidNumberPlacement(tiles: List<HexTile>): Boolean {
        val tileMap = tiles.associateBy { it.coord }
        return tiles.none { tile ->
            val num = tile.diceNumber ?: return@none false
            if (num != 6 && num != 8) return@none false
            HexUtils.hexNeighbors(tile.coord).any { neighborCoord ->
                val neighborNum = tileMap[neighborCoord]?.diceNumber
                neighborNum == 6 || neighborNum == 8
            }
        }
    }

    private fun generatePorts(): List<Port> {
        val shuffledPortTypes = PORT_TYPES.shuffled()
        return PORT_EDGE_POSITIONS.zip(shuffledPortTypes).map { (edge, portType) ->
            val vertices = HexUtils.verticesOfEdge(edge)
            Port(
                vertices = Pair(vertices[0], vertices[1]),
                portType = portType
            )
        }
    }

    /**
     * Creates the standard development card deck (25 cards total).
     */
    fun createDevCardDeck(): MutableList<DevelopmentCardType> {
        val deck = mutableListOf<DevelopmentCardType>()
        repeat(14) { deck.add(DevelopmentCardType.KNIGHT) }
        repeat(5) { deck.add(DevelopmentCardType.VICTORY_POINT) }
        repeat(2) { deck.add(DevelopmentCardType.ROAD_BUILDING) }
        repeat(2) { deck.add(DevelopmentCardType.YEAR_OF_PLENTY) }
        repeat(2) { deck.add(DevelopmentCardType.MONOPOLY) }
        deck.shuffle()
        return deck
    }
}
