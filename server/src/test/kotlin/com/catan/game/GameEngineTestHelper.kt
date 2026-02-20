package com.catan.game

import com.catan.model.*

/**
 * Helper functions for creating test game states.
 */
object GameEngineTestHelper {

    fun createTestState(
        playerCount: Int = 2,
        phase: GamePhase = GamePhase.SETUP_FORWARD,
        turnPhase: TurnPhase = TurnPhase.ROLL_DICE
    ): GameState {
        val (tiles, ports) = BoardGenerator.generateBoard()
        val colors = PlayerColor.entries.toList()
        val players = (0 until playerCount).map { i ->
            Player(
                id = "player$i",
                displayName = "Player $i",
                color = colors[i]
            )
        }.toMutableList()

        return GameState(
            gameId = "test-game",
            tiles = tiles,
            ports = ports,
            players = players,
            currentPlayerIndex = 0,
            phase = phase,
            turnPhase = turnPhase,
            robberLocation = tiles.first { it.hasRobber }.coord,
            devCardDeck = BoardGenerator.createDevCardDeck()
        )
    }

    /**
     * Find a valid vertex for placing a settlement (unoccupied, not too close to others).
     */
    fun findValidVertex(state: GameState, avoidVertices: Set<VertexCoord> = emptySet()): VertexCoord {
        val occupied = state.buildings.map { it.vertex }.toSet()
        val tooClose = occupied.flatMap { HexUtils.adjacentVertices(it) }.toSet()

        return HexUtils.ALL_VERTICES.first { v ->
            v !in occupied && v !in tooClose && v !in avoidVertices
        }
    }

    /**
     * Find a valid edge adjacent to a given vertex.
     */
    fun findEdgeAdjacentToVertex(state: GameState, vertex: VertexCoord): EdgeCoord {
        return HexUtils.edgesOfVertex(vertex).first { state.roadAt(it) == null }
    }

    /**
     * Run through setup phase placing settlements and roads for all players.
     */
    fun completeSetup(state: GameState, engine: GameEngine): GameState {
        var current = state
        val usedVertices = mutableSetOf<VertexCoord>()

        // Forward pass
        for (i in current.players.indices) {
            val vertex = findValidVertex(current, usedVertices)
            usedVertices.add(vertex)
            usedVertices.addAll(HexUtils.adjacentVertices(vertex))

            current = engine.execute(current, GameAction.PlaceSettlement(current.currentPlayer().id, vertex)).getOrThrow()
            val edge = findEdgeAdjacentToVertex(current, vertex)
            current = engine.execute(current, GameAction.PlaceRoad(current.currentPlayer().id, edge)).getOrThrow()
        }

        // Reverse pass
        for (i in current.players.indices.reversed()) {
            val vertex = findValidVertex(current, usedVertices)
            usedVertices.add(vertex)
            usedVertices.addAll(HexUtils.adjacentVertices(vertex))

            current = engine.execute(current, GameAction.PlaceSettlement(current.currentPlayer().id, vertex)).getOrThrow()
            val edge = findEdgeAdjacentToVertex(current, vertex)
            current = engine.execute(current, GameAction.PlaceRoad(current.currentPlayer().id, edge)).getOrThrow()
        }

        return current
    }
}
