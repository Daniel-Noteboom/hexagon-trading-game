package com.catan.ws

import com.catan.model.ServerEvent
import io.ktor.websocket.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
import kotlin.test.*

class GameSessionManagerTest {

    private lateinit var manager: GameSessionManager

    @BeforeTest
    fun setup() {
        manager = GameSessionManager()
    }

    @Test
    fun `addSession tracks session by gameId and playerId`() {
        val session = MockWebSocketSession()
        manager.addSession("game1", "player1", session)

        assertNotNull(manager.getSession("game1", "player1"))
        assertEquals(1, manager.getPlayerCount("game1"))
    }

    @Test
    fun `removeSession removes the session`() {
        val session = MockWebSocketSession()
        manager.addSession("game1", "player1", session)
        manager.removeSession("game1", "player1")

        assertNull(manager.getSession("game1", "player1"))
        assertEquals(0, manager.getPlayerCount("game1"))
    }

    @Test
    fun `broadcast sends to all sessions in a game`() = runBlocking {
        val session1 = MockWebSocketSession()
        val session2 = MockWebSocketSession()
        manager.addSession("game1", "player1", session1)
        manager.addSession("game1", "player2", session2)

        val event = ServerEvent.TurnChanged(playerId = "player1", playerIndex = 0)
        manager.broadcast("game1", event)

        assertEquals(1, session1.sentFrames.size)
        assertEquals(1, session2.sentFrames.size)
    }

    @Test
    fun `sendToPlayer sends only to the target player`() = runBlocking {
        val session1 = MockWebSocketSession()
        val session2 = MockWebSocketSession()
        manager.addSession("game1", "player1", session1)
        manager.addSession("game1", "player2", session2)

        val event = ServerEvent.Error(message = "Invalid action")
        manager.sendToPlayer("game1", "player1", event)

        assertEquals(1, session1.sentFrames.size)
        assertEquals(0, session2.sentFrames.size)
    }

    @Test
    fun `sessions from different games are isolated`() = runBlocking {
        val session1 = MockWebSocketSession()
        val session2 = MockWebSocketSession()
        manager.addSession("game1", "player1", session1)
        manager.addSession("game2", "player2", session2)

        val event = ServerEvent.TurnChanged(playerId = "player1", playerIndex = 0)
        manager.broadcast("game1", event)

        assertEquals(1, session1.sentFrames.size)
        assertEquals(0, session2.sentFrames.size)
    }

    @Test
    fun `getGameSessions returns all sessions for a game`() {
        val session1 = MockWebSocketSession()
        val session2 = MockWebSocketSession()
        manager.addSession("game1", "player1", session1)
        manager.addSession("game1", "player2", session2)

        val gameSessions = manager.getGameSessions("game1")
        assertEquals(2, gameSessions.size)
        assertTrue(gameSessions.containsKey("player1"))
        assertTrue(gameSessions.containsKey("player2"))
    }

    @Test
    fun `getGameSessions returns empty map for unknown game`() {
        val gameSessions = manager.getGameSessions("nonexistent")
        assertTrue(gameSessions.isEmpty())
    }

    @Test
    fun `removeSession cleans up empty game entry`() {
        val session = MockWebSocketSession()
        manager.addSession("game1", "player1", session)
        manager.removeSession("game1", "player1")

        assertEquals(0, manager.getPlayerCount("game1"))
        assertTrue(manager.getGameSessions("game1").isEmpty())
    }

    @Test
    fun `multiple players in same game`() {
        val s1 = MockWebSocketSession()
        val s2 = MockWebSocketSession()
        val s3 = MockWebSocketSession()
        manager.addSession("game1", "p1", s1)
        manager.addSession("game1", "p2", s2)
        manager.addSession("game1", "p3", s3)

        assertEquals(3, manager.getPlayerCount("game1"))

        manager.removeSession("game1", "p2")
        assertEquals(2, manager.getPlayerCount("game1"))
        assertNull(manager.getSession("game1", "p2"))
        assertNotNull(manager.getSession("game1", "p1"))
    }
}

/**
 * Minimal mock WebSocket session for testing GameSessionManager.
 * Only the send(Frame) method is used; everything else throws.
 */
class MockWebSocketSession : io.ktor.server.websocket.WebSocketServerSession {
    val sentFrames = mutableListOf<Frame>()

    override suspend fun send(frame: Frame) {
        sentFrames.add(frame)
    }

    // ---- Unused members below; tests only call send() ----
    override val incoming: kotlinx.coroutines.channels.ReceiveChannel<Frame>
        get() = Channel()
    override val outgoing: kotlinx.coroutines.channels.SendChannel<Frame>
        get() = Channel()
    override val extensions: List<WebSocketExtension<*>> = emptyList()
    override var masking: Boolean = false
    override var maxFrameSize: Long = Long.MAX_VALUE
    override val coroutineContext: kotlin.coroutines.CoroutineContext
        get() = kotlin.coroutines.EmptyCoroutineContext
    override suspend fun flush() {}
    @Deprecated("Not used")
    override fun terminate() {}
    override val call: io.ktor.server.application.ApplicationCall
        get() = throw NotImplementedError()
}
