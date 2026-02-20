package com.catan.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed class ServerEvent {
    @Serializable @SerialName("GAME_STATE_UPDATE")
    data class GameStateUpdate(val state: GameState) : ServerEvent()

    @Serializable @SerialName("DICE_ROLLED")
    data class DiceRolled(val die1: Int, val die2: Int, val playerId: String) : ServerEvent()

    @Serializable @SerialName("BUILDING_PLACED")
    data class BuildingPlaced(val building: Building) : ServerEvent()

    @Serializable @SerialName("ROAD_PLACED")
    data class RoadPlaced(val road: Road) : ServerEvent()

    @Serializable @SerialName("TRADE_OFFERED")
    data class TradeOffered(val trade: TradeOffer) : ServerEvent()

    @Serializable @SerialName("TURN_CHANGED")
    data class TurnChanged(val playerId: String, val playerIndex: Int) : ServerEvent()

    @Serializable @SerialName("GAME_OVER")
    data class GameOver(val winnerId: String, val winnerName: String) : ServerEvent()

    @Serializable @SerialName("ERROR")
    data class Error(val message: String) : ServerEvent()

    @Serializable @SerialName("PLAYER_JOINED")
    data class PlayerJoined(val playerId: String, val displayName: String) : ServerEvent()

    @Serializable @SerialName("GAME_STARTED")
    data class GameStarted(val state: GameState) : ServerEvent()
}
