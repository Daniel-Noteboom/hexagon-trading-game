package com.catan.game

import com.catan.model.*
import kotlin.test.*

class BoardGeneratorTest {

    @Test
    fun `generates exactly 19 tiles`() {
        val (tiles, _) = BoardGenerator.generateBoard()
        assertEquals(19, tiles.size)
    }

    @Test
    fun `correct tile type distribution`() {
        val (tiles, _) = BoardGenerator.generateBoard()
        val counts = tiles.groupingBy { it.tileType }.eachCount()
        assertEquals(4, counts[TileType.FIELDS])
        assertEquals(4, counts[TileType.PASTURE])
        assertEquals(4, counts[TileType.FOREST])
        assertEquals(3, counts[TileType.HILLS])
        assertEquals(3, counts[TileType.MOUNTAINS])
        assertEquals(1, counts[TileType.DESERT])
    }

    @Test
    fun `desert has no dice number`() {
        val (tiles, _) = BoardGenerator.generateBoard()
        val desert = tiles.first { it.tileType == TileType.DESERT }
        assertNull(desert.diceNumber)
    }

    @Test
    fun `all non-desert tiles have a dice number`() {
        val (tiles, _) = BoardGenerator.generateBoard()
        val nonDesert = tiles.filter { it.tileType != TileType.DESERT }
        assertEquals(18, nonDesert.size)
        for (tile in nonDesert) {
            assertNotNull(tile.diceNumber, "Non-desert tile $tile should have a dice number")
            assertTrue(tile.diceNumber in 2..12)
            assertNotEquals(7, tile.diceNumber)
        }
    }

    @Test
    fun `dice number distribution matches standard Catan`() {
        val (tiles, _) = BoardGenerator.generateBoard()
        val numbers = tiles.mapNotNull { it.diceNumber }.sorted()
        assertEquals(18, numbers.size)

        // One each of 2 and 12
        assertEquals(1, numbers.count { it == 2 })
        assertEquals(1, numbers.count { it == 12 })

        // Two each of 3-6 and 8-11
        for (n in listOf(3, 4, 5, 6, 8, 9, 10, 11)) {
            assertEquals(2, numbers.count { it == n }, "Should have exactly 2 of number $n")
        }
    }

    @Test
    fun `no 6 adjacent to 8 (red number rule)`() {
        // Run multiple times since boards are random
        repeat(20) {
            val (tiles, _) = BoardGenerator.generateBoard()
            val tileMap = tiles.associateBy { it.coord }

            for (tile in tiles) {
                if (tile.diceNumber == 6 || tile.diceNumber == 8) {
                    for (neighborCoord in HexUtils.hexNeighbors(tile.coord)) {
                        val neighbor = tileMap[neighborCoord]!!
                        assertFalse(
                            (tile.diceNumber == 6 && neighbor.diceNumber == 8) ||
                            (tile.diceNumber == 8 && neighbor.diceNumber == 6),
                            "Red numbers 6 and 8 should not be adjacent"
                        )
                        assertFalse(
                            tile.diceNumber == 6 && neighbor.diceNumber == 6,
                            "Two 6s should not be adjacent"
                        )
                        assertFalse(
                            tile.diceNumber == 8 && neighbor.diceNumber == 8,
                            "Two 8s should not be adjacent"
                        )
                    }
                }
            }
        }
    }

    @Test
    fun `robber starts on desert`() {
        val (tiles, _) = BoardGenerator.generateBoard()
        val desert = tiles.first { it.tileType == TileType.DESERT }
        assertTrue(desert.hasRobber)
        // No other tile has robber
        val nonDesert = tiles.filter { it.tileType != TileType.DESERT }
        assertTrue(nonDesert.none { it.hasRobber })
    }

    @Test
    fun `exactly 9 ports`() {
        val (_, ports) = BoardGenerator.generateBoard()
        assertEquals(9, ports.size)
    }

    @Test
    fun `correct port type distribution`() {
        val (_, ports) = BoardGenerator.generateBoard()
        val counts = ports.groupingBy { it.portType }.eachCount()
        assertEquals(4, counts[PortType.GENERIC_3_1])
        assertEquals(1, counts[PortType.BRICK_2_1])
        assertEquals(1, counts[PortType.LUMBER_2_1])
        assertEquals(1, counts[PortType.ORE_2_1])
        assertEquals(1, counts[PortType.GRAIN_2_1])
        assertEquals(1, counts[PortType.WOOL_2_1])
    }

    @Test
    fun `port vertices are valid board vertices`() {
        val (_, ports) = BoardGenerator.generateBoard()
        for (port in ports) {
            assertTrue(port.vertices.first in HexUtils.ALL_VERTICES, "Port vertex ${port.vertices.first} should be valid")
            assertTrue(port.vertices.second in HexUtils.ALL_VERTICES, "Port vertex ${port.vertices.second} should be valid")
        }
    }

    @Test
    fun `multiple calls produce different layouts (randomness check)`() {
        val boards = (1..5).map { BoardGenerator.generateBoard().first }
        val layouts = boards.map { tiles -> tiles.map { it.tileType } }
        // At least 2 of 5 should differ
        val unique = layouts.toSet().size
        assertTrue(unique >= 2, "Expected different random layouts, got $unique unique out of 5")
    }

    @Test
    fun `each tile coordinate is unique`() {
        val (tiles, _) = BoardGenerator.generateBoard()
        val coords = tiles.map { it.coord }
        assertEquals(coords.size, coords.toSet().size)
    }

    @Test
    fun `all hex coordinates are used`() {
        val (tiles, _) = BoardGenerator.generateBoard()
        val tileCoords = tiles.map { it.coord }.toSet()
        assertEquals(HexUtils.ALL_HEX_COORDS.toSet(), tileCoords)
    }

    @Test
    fun `createDevCardDeck has correct distribution`() {
        val deck = BoardGenerator.createDevCardDeck()
        assertEquals(25, deck.size)
        assertEquals(14, deck.count { it == DevelopmentCardType.KNIGHT })
        assertEquals(5, deck.count { it == DevelopmentCardType.VICTORY_POINT })
        assertEquals(2, deck.count { it == DevelopmentCardType.ROAD_BUILDING })
        assertEquals(2, deck.count { it == DevelopmentCardType.YEAR_OF_PLENTY })
        assertEquals(2, deck.count { it == DevelopmentCardType.MONOPOLY })
    }
}
