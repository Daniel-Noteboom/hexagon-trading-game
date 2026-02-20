package com.catan.ai

import com.catan.model.GameAction
import com.catan.model.GameState

interface AiStrategy {
    fun chooseAction(state: GameState, playerId: String): GameAction
}
