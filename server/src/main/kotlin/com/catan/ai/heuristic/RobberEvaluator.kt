package com.catan.ai.heuristic

import com.catan.game.HexUtils
import com.catan.model.*

object RobberEvaluator {

    private const val SELF_PENALTY = 10.0
    private const val LEADER_BONUS_PER_VP = 0.5

    fun score(state: GameState, hex: HexCoord, playerId: String): Double {
        val tile = state.tiles.firstOrNull { it.coord == hex } ?: return 0.0
        val diceNumber = tile.diceNumber ?: return 0.1 // Desert gets minimal score

        val probability = VertexEvaluator.diceProbability(diceNumber)
        val vertices = HexUtils.verticesOfHex(hex)

        var opponentImpact = 0.0
        var selfImpact = 0.0

        val maxVp = state.players.maxOf { it.victoryPoints }

        for (vertex in vertices) {
            val building = state.buildingAt(vertex) ?: continue
            val buildingWeight = when (building.type) {
                BuildingType.SETTLEMENT -> 1.0
                BuildingType.CITY -> 2.0
            }

            if (building.playerId == playerId) {
                selfImpact += buildingWeight
            } else {
                val owner = state.playerById(building.playerId)
                val leaderBonus = if (owner != null && owner.victoryPoints >= maxVp && state.players.size > 2) {
                    (owner.victoryPoints - 2) * LEADER_BONUS_PER_VP
                } else {
                    0.0
                }
                opponentImpact += buildingWeight + leaderBonus
            }
        }

        return probability * opponentImpact - selfImpact * SELF_PENALTY
    }
}
