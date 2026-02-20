package com.catan.db

import com.catan.game.BoardGenerator
import com.catan.model.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import kotlin.test.*

class GameRepositoryTest {

    private lateinit var gameRepo: GameRepository
    private lateinit var playerRepo: PlayerRepository
    private lateinit var hostPlayer: PlayerRecord
    private lateinit var db: Database

    @BeforeTest
    fun setup() {
        db = TestDatabaseHelper.setupTestDatabase()
        gameRepo = GameRepository()
        playerRepo = PlayerRepository()
        hostPlayer = playerRepo.createPlayer("Host")
    }

    @AfterTest
    fun teardown() {
        gameRepo.activeGames.clear()
        TestDatabaseHelper.teardownTestDatabase(db)
    }

    @Test
    fun `create game persists to DB and returns record`() {
        val game = gameRepo.createGame(hostPlayer.id)
        assertEquals("LOBBY", game.status)
        assertEquals(hostPlayer.id, game.hostPlayerId)
        assertEquals(4, game.maxPlayers)

        val fetched = gameRepo.getGame(game.id)
        assertNotNull(fetched)
        assertEquals(game.id, fetched.id)
    }

    @Test
    fun `get game state returns from in-memory cache`() {
        val game = gameRepo.createGame(hostPlayer.id)
        val (tiles, ports) = BoardGenerator.generateBoard()
        val state = GameState(
            gameId = game.id,
            tiles = tiles,
            ports = ports,
            players = mutableListOf(),
            robberLocation = tiles.first { it.hasRobber }.coord
        )

        gameRepo.saveGameState(game.id, state)

        val fetched = gameRepo.getGameState(game.id)
        assertNotNull(fetched)
        assertEquals(game.id, fetched.gameId)
    }

    @Test
    fun `update game state updates both DB and cache`() {
        val game = gameRepo.createGame(hostPlayer.id)
        val (tiles, ports) = BoardGenerator.generateBoard()
        val state = GameState(
            gameId = game.id,
            tiles = tiles,
            ports = ports,
            players = mutableListOf(),
            robberLocation = tiles.first { it.hasRobber }.coord
        )

        gameRepo.saveGameState(game.id, state)

        val updatedState = state.copy(currentPlayerIndex = 1)
        gameRepo.saveGameState(game.id, updatedState)

        assertEquals(1, gameRepo.activeGames[game.id]!!.currentPlayerIndex)

        // Clear cache, fetch from DB
        gameRepo.activeGames.clear()
        val fromDb = gameRepo.getGameState(game.id)
        assertNotNull(fromDb)
        assertEquals(1, fromDb.currentPlayerIndex)
    }

    @Test
    fun `list games by status filters correctly`() {
        val game1 = gameRepo.createGame(hostPlayer.id)
        val game2 = gameRepo.createGame(hostPlayer.id)
        gameRepo.updateGameStatus(game2.id, "ACTIVE")

        val lobbyGames = gameRepo.listGames("LOBBY")
        val activeGames = gameRepo.listGames("ACTIVE")

        assertEquals(1, lobbyGames.size)
        assertEquals(game1.id, lobbyGames[0].id)
        assertEquals(1, activeGames.size)
        assertEquals(game2.id, activeGames[0].id)
    }

    @Test
    fun `loadActiveGamesFromDb restores cache after clear`() {
        val game = gameRepo.createGame(hostPlayer.id)
        val (tiles, ports) = BoardGenerator.generateBoard()
        val state = GameState(
            gameId = game.id,
            tiles = tiles,
            ports = ports,
            players = mutableListOf(),
            robberLocation = tiles.first { it.hasRobber }.coord
        )

        gameRepo.saveGameState(game.id, state)
        gameRepo.updateGameStatus(game.id, "ACTIVE")

        gameRepo.activeGames.clear()
        assertTrue(gameRepo.activeGames.isEmpty())

        gameRepo.loadActiveGamesFromDb()
        assertTrue(gameRepo.activeGames.containsKey(game.id))
        assertEquals(game.id, gameRepo.activeGames[game.id]!!.gameId)
    }

    @Test
    fun `add and get game players`() {
        val game = gameRepo.createGame(hostPlayer.id)
        val player2 = playerRepo.createPlayer("Player2")

        gameRepo.addPlayerToGame(game.id, hostPlayer.id, PlayerColor.RED, 0)
        gameRepo.addPlayerToGame(game.id, player2.id, PlayerColor.BLUE, 1)

        val players = gameRepo.getGamePlayers(game.id)
        assertEquals(2, players.size)
        assertEquals(hostPlayer.id, players[0].playerId)
        assertEquals("RED", players[0].color)
        assertEquals(0, players[0].seatIndex)
        assertEquals(player2.id, players[1].playerId)
    }

    @Test
    fun `getGamePlayerCount returns correct count`() {
        val game = gameRepo.createGame(hostPlayer.id)
        assertEquals(0, gameRepo.getGamePlayerCount(game.id))

        gameRepo.addPlayerToGame(game.id, hostPlayer.id, PlayerColor.RED, 0)
        assertEquals(1, gameRepo.getGamePlayerCount(game.id))
    }

    @Test
    fun `isPlayerInGame returns correct boolean`() {
        val game = gameRepo.createGame(hostPlayer.id)
        assertFalse(gameRepo.isPlayerInGame(game.id, hostPlayer.id))

        gameRepo.addPlayerToGame(game.id, hostPlayer.id, PlayerColor.RED, 0)
        assertTrue(gameRepo.isPlayerInGame(game.id, hostPlayer.id))
    }

    @Test
    fun `logAction persists to game_actions table`() {
        val game = gameRepo.createGame(hostPlayer.id)
        gameRepo.logAction(game.id, hostPlayer.id, "ROLL_DICE", "{}")

        val actions = transaction(db) {
            GameActionsTable.selectAll().where { GameActionsTable.gameId eq game.id }.toList()
        }
        assertEquals(1, actions.size)
        assertEquals("ROLL_DICE", actions[0][GameActionsTable.actionType])
    }
}
