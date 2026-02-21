package com.catan.ai

import com.catan.game.BoardGenerator
import com.catan.game.GameEngine
import com.catan.model.*
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertEquals

class AiGameSimulationTest {

    private val engine = GameEngine()
    private val controller = AiController(engine)

    private fun createFullAiGame(
        playerCount: Int = 4,
        difficulty: AiDifficulty = AiDifficulty.MEDIUM
    ): GameState {
        val (tiles, ports) = BoardGenerator.generateBoard()
        val colors = PlayerColor.entries.toList()
        val players = (0 until playerCount).map { i ->
            Player(
                id = "bot$i",
                displayName = "Bot $i",
                color = colors[i],
                isAi = true,
                aiDifficulty = difficulty
            )
        }.toMutableList()

        return GameState(
            gameId = "sim-game",
            tiles = tiles,
            ports = ports,
            players = players,
            currentPlayerIndex = 0,
            phase = GamePhase.SETUP_FORWARD,
            robberLocation = tiles.first { it.hasRobber }.coord,
            devCardDeck = BoardGenerator.createDevCardDeck()
        )
    }

    @Test
    fun `full 4-player AI games mostly complete`() {
        var finished = 0
        val total = 10
        repeat(total) {
            val state = createFullAiGame(playerCount = 4)
            val result = controller.executeAiActions(state)
            if (result.phase == GamePhase.FINISHED) finished++
        }
        assertTrue(finished >= 8, "At least 8/10 4-player games should finish, got $finished/$total")
    }

    @Test
    fun `winner has at least 10 VP`() {
        repeat(5) {
            val state = createFullAiGame(playerCount = 4)
            val result = controller.executeAiActions(state)

            if (result.phase == GamePhase.FINISHED) {
                val winner = result.currentPlayer()
                assertTrue(winner.victoryPoints >= 10,
                    "Winner ${winner.displayName} should have ≥10 VP, has ${winner.victoryPoints}")
            }
        }
    }

    @Test
    fun `no resource counts go negative`() {
        repeat(5) {
            val state = createFullAiGame(playerCount = 4)
            val result = controller.executeAiActions(state)

            for (player in result.players) {
                for ((resource, amount) in player.resources) {
                    assertTrue(amount >= 0,
                        "${player.displayName} has negative $resource: $amount")
                }
            }
        }
    }

    @Test
    fun `building limits are respected`() {
        repeat(5) {
            val state = createFullAiGame(playerCount = 4)
            val result = controller.executeAiActions(state)

            for (player in result.players) {
                val settlements = result.buildings.count {
                    it.playerId == player.id && it.type == BuildingType.SETTLEMENT
                }
                val cities = result.buildings.count {
                    it.playerId == player.id && it.type == BuildingType.CITY
                }
                val roads = result.roads.count { it.playerId == player.id }

                assertTrue(settlements <= 5,
                    "${player.displayName} has $settlements settlements (max 5)")
                assertTrue(cities <= 4,
                    "${player.displayName} has $cities cities (max 4)")
                assertTrue(roads <= 15,
                    "${player.displayName} has $roads roads (max 15)")
            }
        }
    }

    @Test
    fun `3-player EASY AI games mostly complete`() {
        var finished = 0
        val total = 5
        repeat(total) {
            val state = createFullAiGame(playerCount = 3, difficulty = AiDifficulty.EASY)
            val result = controller.executeAiActions(state)
            if (result.phase == GamePhase.FINISHED) finished++
        }
        assertTrue(finished >= 4, "At least 4/5 EASY games should finish, got $finished/$total")
    }

    @Test
    fun `2-player HARD AI games mostly complete`() {
        var finished = 0
        val total = 5
        repeat(total) {
            val state = createFullAiGame(playerCount = 2, difficulty = AiDifficulty.HARD)
            val result = controller.executeAiActions(state)
            if (result.phase == GamePhase.FINISHED) finished++
        }
        assertTrue(finished >= 3, "At least 3/5 HARD 2p games should finish, got $finished/$total")
    }

    @Test
    fun `mixed difficulty game completes`() {
        var finished = 0
        val total = 3
        repeat(total) {
            val (tiles, ports) = BoardGenerator.generateBoard()
            val players = mutableListOf(
                Player("bot0", "Easy Bot", PlayerColor.RED, isAi = true, aiDifficulty = AiDifficulty.EASY),
                Player("bot1", "Medium Bot", PlayerColor.BLUE, isAi = true, aiDifficulty = AiDifficulty.MEDIUM),
                Player("bot2", "Hard Bot", PlayerColor.WHITE, isAi = true, aiDifficulty = AiDifficulty.HARD)
            )

            val state = GameState(
                gameId = "mixed-game",
                tiles = tiles,
                ports = ports,
                players = players,
                currentPlayerIndex = 0,
                phase = GamePhase.SETUP_FORWARD,
                robberLocation = tiles.first { it.hasRobber }.coord,
                devCardDeck = BoardGenerator.createDevCardDeck()
            )

            val result = controller.executeAiActions(state)
            if (result.phase == GamePhase.FINISHED) finished++
        }
        assertTrue(finished >= 2, "At least 2/3 mixed games should finish, got $finished/$total")
    }
}
