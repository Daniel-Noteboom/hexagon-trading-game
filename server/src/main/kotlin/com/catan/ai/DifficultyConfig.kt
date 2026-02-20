package com.catan.ai

import com.catan.model.AiDifficulty

data class DifficultyConfig(
    val difficulty: AiDifficulty,
    val randomnessFactor: Double,
    val tradeAcceptThreshold: Double,
    val considerBlocking: Boolean
) {
    companion object {
        fun forDifficulty(difficulty: AiDifficulty): DifficultyConfig = when (difficulty) {
            AiDifficulty.EASY -> DifficultyConfig(
                difficulty = AiDifficulty.EASY,
                randomnessFactor = 0.5,
                tradeAcceptThreshold = -0.5,
                considerBlocking = false
            )
            AiDifficulty.MEDIUM -> DifficultyConfig(
                difficulty = AiDifficulty.MEDIUM,
                randomnessFactor = 0.15,
                tradeAcceptThreshold = 0.0,
                considerBlocking = false
            )
            AiDifficulty.HARD -> DifficultyConfig(
                difficulty = AiDifficulty.HARD,
                randomnessFactor = 0.0,
                tradeAcceptThreshold = 0.3,
                considerBlocking = true
            )
        }
    }
}
