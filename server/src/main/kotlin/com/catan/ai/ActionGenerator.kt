package com.catan.ai

import com.catan.game.HexUtils
import com.catan.model.*

object ActionGenerator {

    fun generate(state: GameState, playerId: String): List<GameAction> {
        return when (state.phase) {
            GamePhase.SETUP_FORWARD, GamePhase.SETUP_REVERSE -> generateSetupActions(state, playerId)
            GamePhase.MAIN -> generateMainActions(state, playerId)
            GamePhase.FINISHED -> emptyList()
            GamePhase.LOBBY -> emptyList()
        }
    }

    private fun generateSetupActions(state: GameState, playerId: String): List<GameAction> {
        if (state.currentPlayer().id != playerId) return emptyList()

        if (!state.setupState.placedSettlement) {
            return generateSetupSettlementPlacements(state, playerId)
        }
        if (!state.setupState.placedRoad) {
            return generateSetupRoadPlacements(state, playerId)
        }
        return emptyList()
    }

    private fun generateSetupSettlementPlacements(state: GameState, playerId: String): List<GameAction> {
        val occupied = state.buildings.map { it.vertex }.toSet()
        val tooClose = occupied.flatMap { HexUtils.adjacentVertices(it) }.toSet()

        return HexUtils.ALL_VERTICES
            .filter { it !in occupied && it !in tooClose }
            .map { GameAction.PlaceSettlement(playerId, it) }
    }

    private fun generateSetupRoadPlacements(state: GameState, playerId: String): List<GameAction> {
        val lastVertex = state.setupState.lastSettlementVertex ?: return emptyList()
        return HexUtils.edgesOfVertex(lastVertex)
            .filter { state.roadAt(it) == null }
            .map { GameAction.PlaceRoad(playerId, it) }
    }

    private fun generateMainActions(state: GameState, playerId: String): List<GameAction> {
        // Handle discard phase — any player who needs to discard
        if (state.turnPhase == TurnPhase.DISCARD && playerId in state.discardingPlayerIds) {
            return generateDiscardActions(state, playerId)
        }

        // Handle trade response — target of a pending trade
        if (state.pendingTrade != null) {
            val trade = state.pendingTrade!!
            if (playerId != trade.fromPlayerId &&
                (trade.toPlayerId == null || trade.toPlayerId == playerId)
            ) {
                return generateTradeResponseActions(state, playerId)
            }
        }

        // All other actions require being the current player
        if (state.currentPlayer().id != playerId) return emptyList()

        return when (state.turnPhase) {
            TurnPhase.ROLL_DICE -> listOf(GameAction.RollDice(playerId))
            TurnPhase.ROBBER_MOVE -> generateRobberMoveActions(state, playerId)
            TurnPhase.ROBBER_STEAL -> generateStealActions(state, playerId)
            TurnPhase.TRADE_BUILD -> {
                if (state.roadBuildingRoadsLeft > 0) {
                    generateRoadBuildingActions(state, playerId)
                } else {
                    generateTradeBuildActions(state, playerId)
                }
            }
            TurnPhase.DISCARD -> emptyList() // Not this player's turn to discard
            TurnPhase.DONE -> emptyList()
        }
    }

    private fun generateDiscardActions(state: GameState, playerId: String): List<GameAction> {
        val player = state.playerById(playerId) ?: return emptyList()
        val totalResources = player.totalResourceCount()
        val discardCount = totalResources / 2

        val combinations = generateDiscardCombinations(player.resources, discardCount)
        // Cap at 10 to avoid combinatorial explosion
        return combinations.take(10).map { combo ->
            GameAction.DiscardResources(playerId, combo)
        }
    }

    internal fun generateDiscardCombinations(
        resources: Map<ResourceType, Int>,
        target: Int
    ): List<Map<ResourceType, Int>> {
        val resourceTypes = ResourceType.entries.filter { (resources[it] ?: 0) > 0 }
        val results = mutableListOf<Map<ResourceType, Int>>()

        fun backtrack(
            index: Int,
            remaining: Int,
            current: MutableMap<ResourceType, Int>
        ) {
            if (remaining == 0) {
                results.add(current.filter { it.value > 0 }.toMap())
                return
            }
            if (index >= resourceTypes.size) return
            if (results.size >= 50) return // Hard cap

            val type = resourceTypes[index]
            val available = resources[type] ?: 0
            val maxTake = minOf(available, remaining)

            for (take in maxTake downTo 0) {
                if (take > 0) current[type] = take
                backtrack(index + 1, remaining - take, current)
                current.remove(type)
            }
        }

        backtrack(0, target, mutableMapOf())
        return results
    }

    private fun generateRobberMoveActions(state: GameState, playerId: String): List<GameAction> {
        return HexUtils.ALL_HEX_COORDS
            .filter { it != state.robberLocation }
            .filter { hex -> state.tiles.any { it.coord == hex && it.tileType != TileType.DESERT } || state.tiles.any { it.coord == hex } }
            .map { GameAction.MoveRobber(playerId, it) }
    }

    private fun generateStealActions(state: GameState, playerId: String): List<GameAction> {
        val robberHex = state.robberLocation
        val vertices = HexUtils.verticesOfHex(robberHex)
        val targets = vertices.mapNotNull { state.buildingAt(it) }
            .map { it.playerId }
            .filter { it != playerId }
            .filter { pid -> state.playerById(pid)?.totalResourceCount()?.let { it > 0 } == true }
            .distinct()

        return targets.map { GameAction.StealResource(playerId, it) }
    }

    private fun generateRoadBuildingActions(state: GameState, playerId: String): List<GameAction> {
        return generateValidRoadEdges(state, playerId)
            .map { GameAction.PlaceRoad(playerId, it) }
    }

    private fun generateTradeBuildActions(state: GameState, playerId: String): List<GameAction> {
        val player = state.playerById(playerId) ?: return listOf(GameAction.EndTurn(playerId))
        val actions = mutableListOf<GameAction>()

        // Settlements
        if (canAfford(player, SETTLEMENT_COST)) {
            val settlementCount = state.buildings.count { it.playerId == playerId && it.type == BuildingType.SETTLEMENT }
            if (settlementCount < 5) {
                actions.addAll(generateValidSettlementVertices(state, playerId)
                    .map { GameAction.PlaceSettlement(playerId, it) })
            }
        }

        // Cities
        if (canAfford(player, CITY_COST)) {
            val cityCount = state.buildings.count { it.playerId == playerId && it.type == BuildingType.CITY }
            if (cityCount < 4) {
                actions.addAll(
                    state.buildings
                        .filter { it.playerId == playerId && it.type == BuildingType.SETTLEMENT }
                        .map { GameAction.PlaceCity(playerId, it.vertex) }
                )
            }
        }

        // Roads
        if (canAfford(player, ROAD_COST)) {
            val roadCount = state.roads.count { it.playerId == playerId }
            if (roadCount < 15) {
                actions.addAll(generateValidRoadEdges(state, playerId)
                    .map { GameAction.PlaceRoad(playerId, it) })
            }
        }

        // Buy dev card
        if (canAfford(player, DEV_CARD_COST) && state.devCardDeck.isNotEmpty()) {
            actions.add(GameAction.BuyDevelopmentCard(playerId))
        }

        // Play dev cards
        if (!player.hasPlayedDevCardThisTurn) {
            if (DevelopmentCardType.KNIGHT in player.devCards) {
                actions.add(GameAction.PlayKnight(playerId))
            }
            if (DevelopmentCardType.ROAD_BUILDING in player.devCards) {
                val roadCount = state.roads.count { it.playerId == playerId }
                if (roadCount < 15 && generateValidRoadEdges(state, playerId).isNotEmpty()) {
                    actions.add(GameAction.PlayRoadBuilding(playerId))
                }
            }
            if (DevelopmentCardType.YEAR_OF_PLENTY in player.devCards) {
                // Generate a representative subset of year-of-plenty combos
                for (r1 in ResourceType.entries) {
                    for (r2 in ResourceType.entries) {
                        if (r1.ordinal <= r2.ordinal) {
                            actions.add(GameAction.PlayYearOfPlenty(playerId, r1, r2))
                        }
                    }
                }
            }
            if (DevelopmentCardType.MONOPOLY in player.devCards) {
                for (r in ResourceType.entries) {
                    actions.add(GameAction.PlayMonopoly(playerId, r))
                }
            }
        }

        // Bank trades
        actions.addAll(generateBankTradeActions(state, playerId))

        // EndTurn is always available
        actions.add(GameAction.EndTurn(playerId))

        return actions
    }

    private fun generateTradeResponseActions(state: GameState, playerId: String): List<GameAction> {
        val actions = mutableListOf<GameAction>()
        val trade = state.pendingTrade ?: return actions
        val player = state.playerById(playerId) ?: return actions

        // Can accept if player has the requested resources
        if (player.hasResources(trade.requesting)) {
            actions.add(GameAction.AcceptTrade(playerId))
        }
        actions.add(GameAction.DeclineTrade(playerId))
        return actions
    }

    private fun generateValidSettlementVertices(state: GameState, playerId: String): List<VertexCoord> {
        val occupied = state.buildings.map { it.vertex }.toSet()
        val tooClose = occupied.flatMap { HexUtils.adjacentVertices(it) }.toSet()

        val playerRoadEdges = state.roads.filter { it.playerId == playerId }.map { it.edge }
        val playerRoadVertices = playerRoadEdges.flatMap { HexUtils.verticesOfEdge(it) }.toSet()

        return HexUtils.ALL_VERTICES.filter { v ->
            v !in occupied &&
            v !in tooClose &&
            v in playerRoadVertices
        }
    }

    private fun generateValidRoadEdges(state: GameState, playerId: String): List<EdgeCoord> {
        val occupiedEdges = state.roads.map { it.edge }.toSet()

        return HexUtils.ALL_EDGES.filter { edge ->
            if (edge in occupiedEdges) return@filter false

            val edgeVertices = HexUtils.verticesOfEdge(edge)
            edgeVertices.any { vertex ->
                // Has own building here
                val building = state.buildingAt(vertex)
                if (building != null && building.playerId == playerId) return@any true

                // Has adjacent road (not blocked by opponent building)
                val opponentBuilding = building != null && building.playerId != playerId
                if (opponentBuilding) return@any false

                HexUtils.edgesOfVertex(vertex).any { adjEdge ->
                    adjEdge != edge && state.roads.any { it.edge == adjEdge && it.playerId == playerId }
                }
            }
        }
    }

    private fun generateBankTradeActions(state: GameState, playerId: String): List<GameAction> {
        val player = state.playerById(playerId) ?: return emptyList()
        val actions = mutableListOf<GameAction>()

        for (giving in ResourceType.entries) {
            val ratio = getBankTradeRatio(state, playerId, giving)
            val available = player.resources[giving] ?: 0
            if (available >= ratio) {
                for (receiving in ResourceType.entries) {
                    if (receiving != giving) {
                        actions.add(GameAction.BankTrade(playerId, giving, ratio, receiving))
                    }
                }
            }
        }
        return actions
    }

    fun getBankTradeRatio(state: GameState, playerId: String, resource: ResourceType): Int {
        val specificPortType = when (resource) {
            ResourceType.BRICK -> PortType.BRICK_2_1
            ResourceType.LUMBER -> PortType.LUMBER_2_1
            ResourceType.ORE -> PortType.ORE_2_1
            ResourceType.GRAIN -> PortType.GRAIN_2_1
            ResourceType.WOOL -> PortType.WOOL_2_1
        }

        val playerVertices = state.buildings
            .filter { it.playerId == playerId }
            .map { it.vertex }
            .toSet()

        for (port in state.ports) {
            val portVertices = setOf(port.vertices.first, port.vertices.second)
            if (playerVertices.intersect(portVertices).isNotEmpty()) {
                if (port.portType == specificPortType) return 2
            }
        }

        for (port in state.ports) {
            val portVertices = setOf(port.vertices.first, port.vertices.second)
            if (playerVertices.intersect(portVertices).isNotEmpty()) {
                if (port.portType == PortType.GENERIC_3_1) return 3
            }
        }

        return 4
    }

    private fun canAfford(player: Player, cost: Map<ResourceType, Int>): Boolean =
        player.hasResources(cost)

    private val SETTLEMENT_COST = mapOf(
        ResourceType.BRICK to 1, ResourceType.LUMBER to 1,
        ResourceType.GRAIN to 1, ResourceType.WOOL to 1
    )

    private val CITY_COST = mapOf(ResourceType.GRAIN to 2, ResourceType.ORE to 3)

    private val ROAD_COST = mapOf(ResourceType.BRICK to 1, ResourceType.LUMBER to 1)

    private val DEV_CARD_COST = mapOf(
        ResourceType.ORE to 1, ResourceType.GRAIN to 1, ResourceType.WOOL to 1
    )
}
