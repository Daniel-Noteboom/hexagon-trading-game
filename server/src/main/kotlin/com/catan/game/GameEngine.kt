package com.catan.game

import com.catan.model.*

class GameEngine {

    fun execute(state: GameState, action: GameAction): Result<GameState> {
        return try {
            validateCurrentPlayer(state, action)
            val newState = when (action) {
                is GameAction.PlaceSettlement -> placeSettlement(state, action)
                is GameAction.PlaceRoad -> placeRoad(state, action)
                is GameAction.PlaceCity -> placeCity(state, action)
                is GameAction.RollDice -> rollDice(state, action)
                is GameAction.MoveRobber -> moveRobber(state, action)
                is GameAction.StealResource -> stealResource(state, action)
                is GameAction.DiscardResources -> discardResources(state, action)
                is GameAction.OfferTrade -> offerTrade(state, action)
                is GameAction.AcceptTrade -> acceptTrade(state, action)
                is GameAction.DeclineTrade -> declineTrade(state, action)
                is GameAction.BankTrade -> bankTrade(state, action)
                is GameAction.BuyDevelopmentCard -> buyDevCard(state, action)
                is GameAction.PlayKnight -> playKnight(state, action)
                is GameAction.PlayRoadBuilding -> playRoadBuilding(state, action)
                is GameAction.PlayYearOfPlenty -> playYearOfPlenty(state, action)
                is GameAction.PlayMonopoly -> playMonopoly(state, action)
                is GameAction.EndTurn -> endTurn(state, action)
            }
            checkVictory(newState)
            Result.success(newState)
        } catch (e: IllegalArgumentException) {
            Result.failure(e)
        } catch (e: IllegalStateException) {
            Result.failure(e)
        }
    }

    private fun validateCurrentPlayer(state: GameState, action: GameAction) {
        // Discard actions can come from any player who needs to discard
        if (action is GameAction.DiscardResources) return
        // Accept/Decline trade can come from the target player
        if (action is GameAction.AcceptTrade || action is GameAction.DeclineTrade) return

        require(action.playerId == state.currentPlayer().id) {
            "Not your turn. Current player: ${state.currentPlayer().displayName}"
        }
    }

    // ============ Setup Phase ============

    private fun placeSettlement(state: GameState, action: GameAction.PlaceSettlement): GameState {
        val player = state.playerById(action.playerId)
            ?: throw IllegalArgumentException("Player not found")

        when (state.phase) {
            GamePhase.SETUP_FORWARD, GamePhase.SETUP_REVERSE -> placeSettlementSetup(state, action, player)
            GamePhase.MAIN -> placeSettlementMain(state, action, player)
            else -> throw IllegalStateException("Cannot place settlement in ${state.phase}")
        }

        return state
    }

    private fun placeSettlementSetup(state: GameState, action: GameAction.PlaceSettlement, player: Player) {
        require(!state.setupState.placedSettlement) { "Already placed a settlement this setup turn" }
        validateSettlementPlacement(state, action.vertex, player.id, isSetup = true)

        state.buildings.add(Building(action.vertex, player.id, BuildingType.SETTLEMENT))
        player.victoryPoints += 1
        state.setupState.placedSettlement = true
        state.setupState.lastSettlementVertex = action.vertex

        // In reverse setup, grant starting resources from adjacent hexes
        if (state.phase == GamePhase.SETUP_REVERSE) {
            grantStartingResources(state, action.vertex, player)
        }
    }

    private fun placeSettlementMain(state: GameState, action: GameAction.PlaceSettlement, player: Player) {
        require(state.turnPhase == TurnPhase.TRADE_BUILD) { "Cannot build during ${state.turnPhase}" }

        val settlementCount = state.buildings.count { it.playerId == player.id && it.type == BuildingType.SETTLEMENT }
        require(settlementCount < 5) { "Maximum 5 settlements reached" }

        val cost = mapOf(
            ResourceType.BRICK to 1, ResourceType.LUMBER to 1,
            ResourceType.GRAIN to 1, ResourceType.WOOL to 1
        )
        require(player.hasResources(cost)) { "Insufficient resources for settlement" }
        validateSettlementPlacement(state, action.vertex, player.id, isSetup = false)

        player.deductResources(cost)
        state.buildings.add(Building(action.vertex, player.id, BuildingType.SETTLEMENT))
        player.victoryPoints += 1
    }

    private fun validateSettlementPlacement(state: GameState, vertex: VertexCoord, playerId: String, isSetup: Boolean) {
        require(vertex in HexUtils.ALL_VERTICES) { "Invalid vertex position" }
        require(state.buildingAt(vertex) == null) { "Vertex is already occupied" }

        // Distance rule: no settlement within 1 edge of another
        for (adj in HexUtils.adjacentVertices(vertex)) {
            require(state.buildingAt(adj) == null) { "Too close to another settlement (distance rule)" }
        }

        // In main phase, must be adjacent to own road
        if (!isSetup) {
            val adjacentEdges = HexUtils.edgesOfVertex(vertex)
            val hasOwnRoad = adjacentEdges.any { edge ->
                state.roads.any { it.edge == edge && it.playerId == playerId }
            }
            require(hasOwnRoad) { "Settlement must be adjacent to your own road" }
        }
    }

    private fun grantStartingResources(state: GameState, vertex: VertexCoord, player: Player) {
        val adjacentHexes = HexUtils.hexesOfVertex(vertex)
        val tileMap = state.tiles.associateBy { it.coord }
        for (hexCoord in adjacentHexes) {
            val tile = tileMap[hexCoord] ?: continue
            val resource = tile.tileType.resource() ?: continue
            player.addResources(mapOf(resource to 1))
        }
    }

    private fun placeRoad(state: GameState, action: GameAction.PlaceRoad): GameState {
        val player = state.playerById(action.playerId)
            ?: throw IllegalArgumentException("Player not found")

        when (state.phase) {
            GamePhase.SETUP_FORWARD, GamePhase.SETUP_REVERSE -> placeRoadSetup(state, action, player)
            GamePhase.MAIN -> placeRoadMain(state, action, player)
            else -> throw IllegalStateException("Cannot place road in ${state.phase}")
        }

        return state
    }

    private fun placeRoadSetup(state: GameState, action: GameAction.PlaceRoad, player: Player) {
        require(state.setupState.placedSettlement) { "Must place settlement before road in setup" }
        require(!state.setupState.placedRoad) { "Already placed a road this setup turn" }

        val edge = action.edge
        require(edge in HexUtils.ALL_EDGES) { "Invalid edge position" }
        require(state.roadAt(edge) == null) { "Edge is already occupied" }

        // Road must be adjacent to the just-placed settlement
        val lastVertex = state.setupState.lastSettlementVertex
        require(lastVertex != null) { "No settlement placed yet" }
        val verticesOfEdge = HexUtils.verticesOfEdge(edge)
        require(lastVertex in verticesOfEdge) { "Road must be adjacent to your just-placed settlement" }

        state.roads.add(Road(edge, player.id))
        state.setupState.placedRoad = true

        // Advance to next player or next phase
        advanceSetup(state)
    }

    private fun placeRoadMain(state: GameState, action: GameAction.PlaceRoad, player: Player) {
        val roadCount = state.roads.count { it.playerId == player.id }
        require(roadCount < 15) { "Maximum 15 roads reached" }

        if (state.roadBuildingRoadsLeft > 0) {
            // Free road from Road Building dev card
            validateRoadPlacement(state, action.edge, player.id)
            state.roads.add(Road(action.edge, player.id))
            state.roadBuildingRoadsLeft -= 1
            updateLongestRoad(state)
            return
        }

        require(state.turnPhase == TurnPhase.TRADE_BUILD) { "Cannot build during ${state.turnPhase}" }

        val cost = mapOf(ResourceType.BRICK to 1, ResourceType.LUMBER to 1)
        require(player.hasResources(cost)) { "Insufficient resources for road" }
        validateRoadPlacement(state, action.edge, player.id)

        player.deductResources(cost)
        state.roads.add(Road(action.edge, player.id))
        updateLongestRoad(state)
    }

    private fun validateRoadPlacement(state: GameState, edge: EdgeCoord, playerId: String) {
        require(edge in HexUtils.ALL_EDGES) { "Invalid edge position" }
        require(state.roadAt(edge) == null) { "Edge is already occupied" }

        // Must connect to an existing road or building of the same player
        val edgeVertices = HexUtils.verticesOfEdge(edge)
        val connected = edgeVertices.any { vertex ->
            // Has a building here
            val building = state.buildingAt(vertex)
            if (building != null && building.playerId == playerId) return@any true

            // Has an adjacent road (but only if no opponent building blocks at this vertex)
            val opponentBuilding = building != null && building.playerId != playerId
            if (opponentBuilding) return@any false

            HexUtils.edgesOfVertex(vertex).any { adjEdge ->
                adjEdge != edge && state.roads.any { it.edge == adjEdge && it.playerId == playerId }
            }
        }
        require(connected) { "Road must connect to your existing road or building" }
    }

    private fun advanceSetup(state: GameState) {
        state.setupState = SetupState()

        when (state.phase) {
            GamePhase.SETUP_FORWARD -> {
                if (state.currentPlayerIndex < state.players.size - 1) {
                    state.currentPlayerIndex += 1
                } else {
                    // Switch to reverse
                    state.phase = GamePhase.SETUP_REVERSE
                    // currentPlayerIndex stays at last player
                }
            }
            GamePhase.SETUP_REVERSE -> {
                if (state.currentPlayerIndex > 0) {
                    state.currentPlayerIndex -= 1
                } else {
                    // Setup complete, move to main game
                    state.phase = GamePhase.MAIN
                    state.turnPhase = TurnPhase.ROLL_DICE
                    state.currentPlayerIndex = 0
                }
            }
            else -> {}
        }
    }

    // ============ Main Phase: Dice ============

    private fun rollDice(state: GameState, action: GameAction.RollDice): GameState {
        require(state.phase == GamePhase.MAIN) { "Cannot roll dice in ${state.phase}" }
        require(state.turnPhase == TurnPhase.ROLL_DICE) { "Cannot roll dice during ${state.turnPhase}" }

        val die1 = (1..6).random()
        val die2 = (1..6).random()
        state.diceRoll = Pair(die1, die2)
        val total = die1 + die2

        if (total == 7) {
            handleSeven(state)
        } else {
            distributeResources(state, total)
            state.turnPhase = TurnPhase.TRADE_BUILD
        }

        return state
    }

    private fun handleSeven(state: GameState) {
        // Players with >7 cards must discard half
        val mustDiscard = state.players.filter { it.totalResourceCount() > 7 }
        if (mustDiscard.isNotEmpty()) {
            state.discardingPlayerIds = mustDiscard.map { it.id }.toMutableList()
            state.turnPhase = TurnPhase.DISCARD
        } else {
            state.turnPhase = TurnPhase.ROBBER_MOVE
        }
    }

    private fun distributeResources(state: GameState, diceTotal: Int) {
        val tileMap = state.tiles.associateBy { it.coord }
        val matchingTiles = state.tiles.filter {
            it.diceNumber == diceTotal && !it.hasRobber
        }

        for (tile in matchingTiles) {
            val resource = tile.tileType.resource() ?: continue
            val vertices = HexUtils.verticesOfHex(tile.coord)
            for (vertex in vertices) {
                val building = state.buildingAt(vertex) ?: continue
                val amount = when (building.type) {
                    BuildingType.SETTLEMENT -> 1
                    BuildingType.CITY -> 2
                }
                val player = state.playerById(building.playerId) ?: continue
                player.addResources(mapOf(resource to amount))
            }
        }
    }

    // ============ Main Phase: Robber ============

    private fun moveRobber(state: GameState, action: GameAction.MoveRobber): GameState {
        require(state.phase == GamePhase.MAIN) { "Cannot move robber in ${state.phase}" }
        require(state.turnPhase == TurnPhase.ROBBER_MOVE) { "Cannot move robber during ${state.turnPhase}" }
        require(action.hex in HexUtils.ALL_HEX_COORDS) { "Invalid hex" }
        require(action.hex != state.robberLocation) { "Must move robber to a different hex" }

        // Move robber
        val tiles = state.tiles.toMutableList()
        val oldIdx = tiles.indexOfFirst { it.hasRobber }
        tiles[oldIdx] = tiles[oldIdx].copy(hasRobber = false)
        val newIdx = tiles.indexOfFirst { it.coord == action.hex }
        tiles[newIdx] = tiles[newIdx].copy(hasRobber = true)

        // Replace tiles list (it's a val in data class, so we work with mutable copy)
        val newState = state.copy(tiles = tiles, robberLocation = action.hex)

        // Check if there are players to steal from
        val stealTargets = getStealTargets(newState, action.hex, action.playerId)
        if (stealTargets.isEmpty()) {
            newState.turnPhase = TurnPhase.TRADE_BUILD
        } else {
            newState.turnPhase = TurnPhase.ROBBER_STEAL
        }

        return newState
    }

    private fun getStealTargets(state: GameState, hex: HexCoord, thiefId: String): List<String> {
        val vertices = HexUtils.verticesOfHex(hex)
        return vertices.mapNotNull { state.buildingAt(it) }
            .map { it.playerId }
            .filter { it != thiefId }
            .filter { pid -> state.playerById(pid)?.totalResourceCount()?.let { it > 0 } == true }
            .distinct()
    }

    private fun stealResource(state: GameState, action: GameAction.StealResource): GameState {
        require(state.phase == GamePhase.MAIN) { "Cannot steal in ${state.phase}" }
        require(state.turnPhase == TurnPhase.ROBBER_STEAL) { "Cannot steal during ${state.turnPhase}" }

        val target = state.playerById(action.targetPlayerId)
            ?: throw IllegalArgumentException("Target player not found")
        val thief = state.playerById(action.playerId)
            ?: throw IllegalArgumentException("Player not found")

        val validTargets = getStealTargets(state, state.robberLocation, action.playerId)
        require(action.targetPlayerId in validTargets) { "Target player has no building on the robber hex" }

        require(target.totalResourceCount() > 0) { "Target has no resources" }

        // Steal a random resource
        val targetResources = target.resources.entries
            .filter { it.value > 0 }
            .flatMap { entry -> List(entry.value) { entry.key } }

        if (targetResources.isNotEmpty()) {
            val stolen = targetResources.random()
            target.resources[stolen] = (target.resources[stolen] ?: 0) - 1
            thief.resources[stolen] = (thief.resources[stolen] ?: 0) + 1
        }

        state.turnPhase = TurnPhase.TRADE_BUILD
        return state
    }

    private fun discardResources(state: GameState, action: GameAction.DiscardResources): GameState {
        require(state.turnPhase == TurnPhase.DISCARD) { "Not in discard phase" }
        require(action.playerId in state.discardingPlayerIds) { "You don't need to discard" }

        val player = state.playerById(action.playerId)
            ?: throw IllegalArgumentException("Player not found")

        val discardCount = action.resources.values.sum()
        val requiredDiscard = player.totalResourceCount() / 2
        require(discardCount == requiredDiscard) { "Must discard exactly $requiredDiscard cards, got $discardCount" }
        require(player.hasResources(action.resources)) { "Cannot discard resources you don't have" }

        player.deductResources(action.resources)
        state.discardingPlayerIds.remove(action.playerId)

        if (state.discardingPlayerIds.isEmpty()) {
            state.turnPhase = TurnPhase.ROBBER_MOVE
        }

        return state
    }

    // ============ Main Phase: Building ============

    private fun placeCity(state: GameState, action: GameAction.PlaceCity): GameState {
        require(state.phase == GamePhase.MAIN) { "Cannot place city in ${state.phase}" }
        require(state.turnPhase == TurnPhase.TRADE_BUILD) { "Cannot build during ${state.turnPhase}" }

        val player = state.playerById(action.playerId)
            ?: throw IllegalArgumentException("Player not found")

        val cityCount = state.buildings.count { it.playerId == player.id && it.type == BuildingType.CITY }
        require(cityCount < 4) { "Maximum 4 cities reached" }

        val cost = mapOf(ResourceType.GRAIN to 2, ResourceType.ORE to 3)
        require(player.hasResources(cost)) { "Insufficient resources for city" }

        val building = state.buildingAt(action.vertex)
        require(building != null) { "No building at this vertex" }
        require(building.playerId == player.id) { "Not your building" }
        require(building.type == BuildingType.SETTLEMENT) { "Can only upgrade settlements to cities" }

        player.deductResources(cost)

        // Replace settlement with city
        val idx = state.buildings.indexOfFirst { it.vertex == action.vertex }
        state.buildings[idx] = Building(action.vertex, player.id, BuildingType.CITY)
        player.victoryPoints += 1 // Settlement was 1 VP, city is 2 VP (net +1)

        return state
    }

    // ============ Main Phase: Trading ============

    private fun offerTrade(state: GameState, action: GameAction.OfferTrade): GameState {
        require(state.phase == GamePhase.MAIN) { "Cannot trade in ${state.phase}" }
        require(state.turnPhase == TurnPhase.TRADE_BUILD) { "Cannot trade during ${state.turnPhase}" }

        val player = state.playerById(action.playerId)!!
        require(player.hasResources(action.offering)) { "Cannot offer resources you don't have" }
        require(state.pendingTrade == null) { "There is already a pending trade" }

        state.pendingTrade = TradeOffer(
            fromPlayerId = action.playerId,
            toPlayerId = action.targetPlayerId,
            offering = action.offering,
            requesting = action.requesting,
            id = java.util.UUID.randomUUID().toString()
        )

        return state
    }

    private fun acceptTrade(state: GameState, action: GameAction.AcceptTrade): GameState {
        val trade = state.pendingTrade
            ?: throw IllegalStateException("No pending trade")

        require(action.playerId != trade.fromPlayerId) { "Cannot accept your own trade" }
        if (trade.toPlayerId != null) {
            require(action.playerId == trade.toPlayerId) { "This trade is not directed at you" }
        }

        val offerer = state.playerById(trade.fromPlayerId)!!
        val accepter = state.playerById(action.playerId)!!

        require(offerer.hasResources(trade.offering)) { "Offerer no longer has the resources" }
        require(accepter.hasResources(trade.requesting)) { "You don't have the requested resources" }

        offerer.deductResources(trade.offering)
        accepter.deductResources(trade.requesting)
        offerer.addResources(trade.requesting)
        accepter.addResources(trade.offering)

        state.pendingTrade = null
        return state
    }

    private fun declineTrade(state: GameState, action: GameAction.DeclineTrade): GameState {
        require(state.pendingTrade != null) { "No pending trade" }
        state.pendingTrade = null
        return state
    }

    private fun bankTrade(state: GameState, action: GameAction.BankTrade): GameState {
        require(state.phase == GamePhase.MAIN) { "Cannot trade in ${state.phase}" }
        require(state.turnPhase == TurnPhase.TRADE_BUILD) { "Cannot trade during ${state.turnPhase}" }

        val player = state.playerById(action.playerId)!!
        require(action.giving != action.receiving) { "Cannot trade same resource" }

        // Determine the trade ratio
        val ratio = getBankTradeRatio(state, player.id, action.giving)
        require(action.givingAmount == ratio) { "Must give exactly $ratio of ${action.giving}" }
        require(player.hasResources(mapOf(action.giving to ratio))) { "Insufficient resources" }

        player.deductResources(mapOf(action.giving to ratio))
        player.addResources(mapOf(action.receiving to 1))

        return state
    }

    private fun getBankTradeRatio(state: GameState, playerId: String, resource: ResourceType): Int {
        // Check for 2:1 specific port
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

        // Check for 3:1 generic port
        for (port in state.ports) {
            val portVertices = setOf(port.vertices.first, port.vertices.second)
            if (playerVertices.intersect(portVertices).isNotEmpty()) {
                if (port.portType == PortType.GENERIC_3_1) return 3
            }
        }

        return 4 // default ratio
    }

    // ============ Main Phase: Development Cards ============

    private fun buyDevCard(state: GameState, action: GameAction.BuyDevelopmentCard): GameState {
        require(state.phase == GamePhase.MAIN) { "Cannot buy dev card in ${state.phase}" }
        require(state.turnPhase == TurnPhase.TRADE_BUILD) { "Cannot buy during ${state.turnPhase}" }

        val player = state.playerById(action.playerId)!!
        val cost = mapOf(ResourceType.ORE to 1, ResourceType.GRAIN to 1, ResourceType.WOOL to 1)
        require(player.hasResources(cost)) { "Insufficient resources for development card" }
        require(state.devCardDeck.isNotEmpty()) { "No development cards remaining" }

        player.deductResources(cost)
        val card = state.devCardDeck.removeFirst()
        player.newDevCards.add(card) // Can't play until next turn

        if (card == DevelopmentCardType.VICTORY_POINT) {
            player.victoryPoints += 1
        }

        return state
    }

    private fun playKnight(state: GameState, action: GameAction.PlayKnight): GameState {
        require(state.phase == GamePhase.MAIN) { "Cannot play dev card in ${state.phase}" }
        validateDevCardPlay(state, action.playerId, DevelopmentCardType.KNIGHT)

        val player = state.playerById(action.playerId)!!
        player.devCards.remove(DevelopmentCardType.KNIGHT)
        player.knightsPlayed += 1
        player.hasPlayedDevCardThisTurn = true

        updateLargestArmy(state)
        state.turnPhase = TurnPhase.ROBBER_MOVE

        return state
    }

    private fun playRoadBuilding(state: GameState, action: GameAction.PlayRoadBuilding): GameState {
        require(state.phase == GamePhase.MAIN) { "Cannot play dev card in ${state.phase}" }
        validateDevCardPlay(state, action.playerId, DevelopmentCardType.ROAD_BUILDING)

        val player = state.playerById(action.playerId)!!
        player.devCards.remove(DevelopmentCardType.ROAD_BUILDING)
        player.hasPlayedDevCardThisTurn = true
        state.roadBuildingRoadsLeft = 2

        return state
    }

    private fun playYearOfPlenty(state: GameState, action: GameAction.PlayYearOfPlenty): GameState {
        require(state.phase == GamePhase.MAIN) { "Cannot play dev card in ${state.phase}" }
        validateDevCardPlay(state, action.playerId, DevelopmentCardType.YEAR_OF_PLENTY)

        val player = state.playerById(action.playerId)!!
        player.devCards.remove(DevelopmentCardType.YEAR_OF_PLENTY)
        player.hasPlayedDevCardThisTurn = true
        player.addResources(mapOf(action.resource1 to 1, action.resource2 to 1))

        return state
    }

    private fun playMonopoly(state: GameState, action: GameAction.PlayMonopoly): GameState {
        require(state.phase == GamePhase.MAIN) { "Cannot play dev card in ${state.phase}" }
        validateDevCardPlay(state, action.playerId, DevelopmentCardType.MONOPOLY)

        val player = state.playerById(action.playerId)!!
        player.devCards.remove(DevelopmentCardType.MONOPOLY)
        player.hasPlayedDevCardThisTurn = true

        var totalStolen = 0
        for (other in state.players) {
            if (other.id == player.id) continue
            val amount = other.resources[action.resource] ?: 0
            if (amount > 0) {
                other.resources[action.resource] = 0
                totalStolen += amount
            }
        }
        player.addResources(mapOf(action.resource to totalStolen))

        return state
    }

    private fun validateDevCardPlay(state: GameState, playerId: String, cardType: DevelopmentCardType) {
        val player = state.playerById(playerId)!!
        require(!player.hasPlayedDevCardThisTurn) { "Already played a development card this turn" }
        require(cardType in player.devCards) { "You don't have a ${cardType.name} card to play" }
    }

    // ============ End Turn ============

    private fun endTurn(state: GameState, action: GameAction.EndTurn): GameState {
        require(state.phase == GamePhase.MAIN) { "Cannot end turn in ${state.phase}" }
        require(state.turnPhase == TurnPhase.TRADE_BUILD) { "Cannot end turn during ${state.turnPhase}" }
        require(state.roadBuildingRoadsLeft == 0) { "Must place remaining Road Building roads" }

        val player = state.currentPlayer()

        // Move new dev cards to playable deck
        player.devCards.addAll(player.newDevCards)
        player.newDevCards.clear()
        player.hasPlayedDevCardThisTurn = false

        // Advance to next player
        state.currentPlayerIndex = state.nextPlayerIndex()
        state.turnPhase = TurnPhase.ROLL_DICE
        state.diceRoll = null
        state.pendingTrade = null

        return state
    }

    // ============ Victory ============

    private fun checkVictory(state: GameState) {
        if (state.phase != GamePhase.MAIN) return
        val player = state.currentPlayer()
        if (player.victoryPoints >= 10) {
            state.phase = GamePhase.FINISHED
        }
    }

    // ============ Longest Road / Largest Army ============

    private fun updateLongestRoad(state: GameState) {
        var longestLength = 4 // minimum 5 to claim
        var longestPlayer: String? = state.longestRoadHolder

        // If current holder still has longest, check if someone beat them
        if (longestPlayer != null) {
            longestLength = LongestRoadCalculator.calculate(longestPlayer, state.roads, state.buildings) - 1
        }

        for (player in state.players) {
            val length = LongestRoadCalculator.calculate(player.id, state.roads, state.buildings)
            if (length >= 5 && length > longestLength) {
                longestLength = length
                longestPlayer = player.id
            }
        }

        if (longestPlayer != state.longestRoadHolder) {
            // Remove VP from old holder
            state.longestRoadHolder?.let { oldId ->
                state.playerById(oldId)?.let { it.victoryPoints -= 2 }
            }
            // Add VP to new holder
            longestPlayer?.let { newId ->
                state.playerById(newId)?.let { it.victoryPoints += 2 }
            }
            state.longestRoadHolder = longestPlayer
        }
    }

    private fun updateLargestArmy(state: GameState) {
        var largestCount = 2 // minimum 3 to claim
        var largestPlayer: String? = state.largestArmyHolder

        if (largestPlayer != null) {
            largestCount = state.playerById(largestPlayer)?.knightsPlayed?.minus(1) ?: 2
        }

        for (player in state.players) {
            if (player.knightsPlayed >= 3 && player.knightsPlayed > largestCount) {
                largestCount = player.knightsPlayed
                largestPlayer = player.id
            }
        }

        if (largestPlayer != state.largestArmyHolder) {
            state.largestArmyHolder?.let { oldId ->
                state.playerById(oldId)?.let { it.victoryPoints -= 2 }
            }
            largestPlayer?.let { newId ->
                state.playerById(newId)?.let { it.victoryPoints += 2 }
            }
            state.largestArmyHolder = largestPlayer
        }
    }
}
