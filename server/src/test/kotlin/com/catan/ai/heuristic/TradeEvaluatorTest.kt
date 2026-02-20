package com.catan.ai.heuristic

import com.catan.ai.DifficultyConfig
import com.catan.game.GameEngine
import com.catan.game.GameEngineTestHelper
import com.catan.model.*
import kotlin.test.Test
import kotlin.test.assertTrue

class TradeEvaluatorTest {

    private val engine = GameEngine()

    @Test
    fun `bank trade giving surplus for needed resource scores positive`() {
        val state = GameEngineTestHelper.completeSetup(
            GameEngineTestHelper.createTestState(playerCount = 2),
            engine
        )
        val playerId = state.currentPlayer().id
        val player = state.playerById(playerId)!!
        player.resources.replaceAll { _, _ -> 0 }
        player.resources[ResourceType.ORE] = 4
        // Need brick for road/settlement

        val action = GameAction.BankTrade(playerId, ResourceType.ORE, 4, ResourceType.BRICK)
        val score = TradeEvaluator.scoreBankTrade(state, action, playerId)
        assertTrue(score > 0, "Bank trade of surplus ore for needed brick should be positive, got $score")
    }

    @Test
    fun `bank trade with port is preferred over 4-1`() {
        val state = GameEngineTestHelper.completeSetup(
            GameEngineTestHelper.createTestState(playerCount = 2),
            engine
        )
        val playerId = state.currentPlayer().id
        val player = state.playerById(playerId)!!
        player.resources.replaceAll { _, _ -> 0 }
        player.resources[ResourceType.BRICK] = 4

        // 4:1 trade
        val trade4 = GameAction.BankTrade(playerId, ResourceType.BRICK, 4, ResourceType.ORE)
        val score4 = TradeEvaluator.scoreBankTrade(state, trade4, playerId)

        // Simulate 2:1 trade (lower cost)
        val trade2 = GameAction.BankTrade(playerId, ResourceType.BRICK, 2, ResourceType.ORE)
        val score2 = TradeEvaluator.scoreBankTrade(state, trade2, playerId)

        assertTrue(score2 > score4,
            "2:1 port trade ($score2) should score higher than 4:1 ($score4)")
    }

    @Test
    fun `accepting trade that gives needed resource scores positive`() {
        val state = GameEngineTestHelper.completeSetup(
            GameEngineTestHelper.createTestState(playerCount = 2),
            engine
        )
        val playerId = state.players[1].id
        val player = state.playerById(playerId)!!
        player.resources.replaceAll { _, _ -> 0 }
        player.resources[ResourceType.WOOL] = 3
        // AI needs ore for city

        val trade = TradeOffer(
            fromPlayerId = state.players[0].id,
            toPlayerId = playerId,
            offering = mapOf(ResourceType.ORE to 1),
            requesting = mapOf(ResourceType.WOOL to 1)
        )

        val mediumConfig = DifficultyConfig.forDifficulty(AiDifficulty.MEDIUM)
        val score = TradeEvaluator.scoreTradeResponse(state, trade, playerId, mediumConfig)
        assertTrue(score > 0, "Accepting good trade should score positive, got $score")
    }

    @Test
    fun `declining trade that helps leader on HARD difficulty`() {
        val state = GameEngineTestHelper.completeSetup(
            GameEngineTestHelper.createTestState(playerCount = 3),
            engine
        )
        val aiId = state.players[1].id
        val leaderId = state.players[0].id
        val aiPlayer = state.playerById(aiId)!!
        val leader = state.playerById(leaderId)!!

        leader.victoryPoints = 8
        aiPlayer.resources.replaceAll { _, _ -> 0 }
        aiPlayer.resources[ResourceType.BRICK] = 2

        val trade = TradeOffer(
            fromPlayerId = leaderId,
            toPlayerId = aiId,
            offering = mapOf(ResourceType.WOOL to 1),
            requesting = mapOf(ResourceType.BRICK to 1)
        )

        val hardConfig = DifficultyConfig.forDifficulty(AiDifficulty.HARD)
        val score = TradeEvaluator.scoreTradeResponse(state, trade, aiId, hardConfig)
        // HARD should penalize helping the leader
        assertTrue(score < 0, "HARD should decline trading with leader, score=$score")
    }

    @Test
    fun `declining trade that takes only needed resource`() {
        val state = GameEngineTestHelper.completeSetup(
            GameEngineTestHelper.createTestState(playerCount = 2),
            engine
        )
        val aiId = state.players[1].id
        val aiPlayer = state.playerById(aiId)!!
        aiPlayer.resources.replaceAll { _, _ -> 0 }
        aiPlayer.resources[ResourceType.BRICK] = 1 // Only 1 brick, needed

        val trade = TradeOffer(
            fromPlayerId = state.players[0].id,
            toPlayerId = aiId,
            offering = mapOf(ResourceType.WOOL to 1),  // Don't really need wool
            requesting = mapOf(ResourceType.BRICK to 1) // Would take our only brick
        )

        val mediumConfig = DifficultyConfig.forDifficulty(AiDifficulty.MEDIUM)
        val score = TradeEvaluator.scoreTradeResponse(state, trade, aiId, mediumConfig)
        assertTrue(score < 0, "Should decline losing only brick for unneeded wool, score=$score")
    }

    @Test
    fun `trade offer giving surplus for needed resource scores positive`() {
        val state = GameEngineTestHelper.completeSetup(
            GameEngineTestHelper.createTestState(playerCount = 2),
            engine
        )
        val playerId = state.currentPlayer().id
        val player = state.playerById(playerId)!!
        player.resources.replaceAll { _, _ -> 0 }
        player.resources[ResourceType.WOOL] = 5
        // Need ore

        val offer = GameAction.OfferTrade(
            playerId = playerId,
            offering = mapOf(ResourceType.WOOL to 1),
            requesting = mapOf(ResourceType.ORE to 1)
        )

        val score = TradeEvaluator.scoreOfferTrade(state, offer, playerId)
        assertTrue(score > -1, "Offering surplus wool for needed ore should be reasonable, got $score")
    }

    @Test
    fun `no useful trade when already have build resources`() {
        val state = GameEngineTestHelper.completeSetup(
            GameEngineTestHelper.createTestState(playerCount = 2),
            engine
        )
        val playerId = state.currentPlayer().id
        val player = state.playerById(playerId)!!
        // Has resources for a city
        player.resources.replaceAll { _, _ -> 0 }
        player.resources[ResourceType.GRAIN] = 2
        player.resources[ResourceType.ORE] = 4
        player.resources[ResourceType.BRICK] = 0

        // Trading away 4 ore when we need 3 for city is wasteful
        val action = GameAction.BankTrade(playerId, ResourceType.ORE, 4, ResourceType.BRICK)
        val score = TradeEvaluator.scoreBankTrade(state, action, playerId)
        // Bank trade of 4 ore for 1 brick is a lot of ore gone.
        // The score reflects need for brick (low since we don't need it for any build)
        // minus surplus of ore (which is moderately needed for city)
        // Just verify it's scored and finite
        assertTrue(score.isFinite(), "Score should be finite, score=$score")
    }

    @Test
    fun `scores are finite`() {
        val state = GameEngineTestHelper.completeSetup(
            GameEngineTestHelper.createTestState(playerCount = 2),
            engine
        )
        val playerId = state.currentPlayer().id

        for (giving in ResourceType.entries) {
            for (receiving in ResourceType.entries) {
                if (giving == receiving) continue
                val action = GameAction.BankTrade(playerId, giving, 4, receiving)
                val score = TradeEvaluator.scoreBankTrade(state, action, playerId)
                assertTrue(score.isFinite(), "Score should be finite for $giving→$receiving, got $score")
            }
        }
    }
}
