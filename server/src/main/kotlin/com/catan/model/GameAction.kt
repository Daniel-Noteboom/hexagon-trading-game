package com.catan.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed class GameAction {
    abstract val playerId: String

    @Serializable @SerialName("ROLL_DICE")
    data class RollDice(override val playerId: String) : GameAction()

    @Serializable @SerialName("PLACE_SETTLEMENT")
    data class PlaceSettlement(override val playerId: String, val vertex: VertexCoord) : GameAction()

    @Serializable @SerialName("PLACE_CITY")
    data class PlaceCity(override val playerId: String, val vertex: VertexCoord) : GameAction()

    @Serializable @SerialName("PLACE_ROAD")
    data class PlaceRoad(override val playerId: String, val edge: EdgeCoord) : GameAction()

    @Serializable @SerialName("MOVE_ROBBER")
    data class MoveRobber(override val playerId: String, val hex: HexCoord) : GameAction()

    @Serializable @SerialName("STEAL_RESOURCE")
    data class StealResource(override val playerId: String, val targetPlayerId: String) : GameAction()

    @Serializable @SerialName("DISCARD_RESOURCES")
    data class DiscardResources(override val playerId: String, val resources: Map<ResourceType, Int>) : GameAction()

    @Serializable @SerialName("OFFER_TRADE")
    data class OfferTrade(
        override val playerId: String,
        val targetPlayerId: String? = null,
        val offering: Map<ResourceType, Int>,
        val requesting: Map<ResourceType, Int>
    ) : GameAction()

    @Serializable @SerialName("ACCEPT_TRADE")
    data class AcceptTrade(override val playerId: String) : GameAction()

    @Serializable @SerialName("DECLINE_TRADE")
    data class DeclineTrade(override val playerId: String) : GameAction()

    @Serializable @SerialName("BANK_TRADE")
    data class BankTrade(
        override val playerId: String,
        val giving: ResourceType,
        val givingAmount: Int,
        val receiving: ResourceType
    ) : GameAction()

    @Serializable @SerialName("BUY_DEVELOPMENT_CARD")
    data class BuyDevelopmentCard(override val playerId: String) : GameAction()

    @Serializable @SerialName("PLAY_KNIGHT")
    data class PlayKnight(override val playerId: String) : GameAction()

    @Serializable @SerialName("PLAY_ROAD_BUILDING")
    data class PlayRoadBuilding(override val playerId: String) : GameAction()

    @Serializable @SerialName("PLAY_YEAR_OF_PLENTY")
    data class PlayYearOfPlenty(
        override val playerId: String,
        val resource1: ResourceType,
        val resource2: ResourceType
    ) : GameAction()

    @Serializable @SerialName("PLAY_MONOPOLY")
    data class PlayMonopoly(override val playerId: String, val resource: ResourceType) : GameAction()

    @Serializable @SerialName("END_TURN")
    data class EndTurn(override val playerId: String) : GameAction()
}
