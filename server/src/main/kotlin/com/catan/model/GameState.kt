package com.catan.model

import kotlinx.serialization.Serializable

@Serializable
enum class GamePhase {
    LOBBY, SETUP_FORWARD, SETUP_REVERSE, MAIN, FINISHED
}

@Serializable
enum class TurnPhase {
    ROLL_DICE, ROBBER_MOVE, ROBBER_STEAL, DISCARD, TRADE_BUILD, DONE
}

@Serializable
data class TradeOffer(
    val fromPlayerId: String,
    val toPlayerId: String? = null,
    val offering: Map<ResourceType, Int>,
    val requesting: Map<ResourceType, Int>,
    val id: String = ""
)

@Serializable
data class SetupState(
    var placedSettlement: Boolean = false,
    var placedRoad: Boolean = false,
    var lastSettlementVertex: VertexCoord? = null
)

@Serializable
data class GameState(
    val gameId: String,
    val tiles: List<HexTile>,
    val ports: List<Port>,
    val buildings: MutableList<Building> = mutableListOf(),
    val roads: MutableList<Road> = mutableListOf(),
    val players: MutableList<Player>,
    var currentPlayerIndex: Int = 0,
    var phase: GamePhase = GamePhase.SETUP_FORWARD,
    var turnPhase: TurnPhase = TurnPhase.ROLL_DICE,
    var robberLocation: HexCoord,
    var longestRoadHolder: String? = null,
    var largestArmyHolder: String? = null,
    val devCardDeck: MutableList<DevelopmentCardType> = mutableListOf(),
    var diceRoll: Pair<Int, Int>? = null,
    var pendingTrade: TradeOffer? = null,
    var setupState: SetupState = SetupState(),
    var discardingPlayerIds: MutableList<String> = mutableListOf(),
    var roadBuildingRoadsLeft: Int = 0
) {
    fun currentPlayer(): Player = players[currentPlayerIndex]

    fun playerById(id: String): Player? = players.find { it.id == id }

    fun buildingAt(vertex: VertexCoord): Building? = buildings.find { it.vertex == vertex }

    fun roadAt(edge: EdgeCoord): Road? = roads.find { it.edge == edge }

    fun nextPlayerIndex(): Int = (currentPlayerIndex + 1) % players.size

    fun prevPlayerIndex(): Int = (currentPlayerIndex - 1 + players.size) % players.size
}
