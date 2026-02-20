package com.catan.db

import org.jetbrains.exposed.sql.Database
import kotlin.test.*

class PlayerRepositoryTest {

    private lateinit var repo: PlayerRepository
    private lateinit var db: Database

    @BeforeTest
    fun setup() {
        db = TestDatabaseHelper.setupTestDatabase()
        repo = PlayerRepository()
    }

    @AfterTest
    fun teardown() {
        TestDatabaseHelper.teardownTestDatabase(db)
    }

    @Test
    fun `create player returns valid UUID and token`() {
        val player = repo.createPlayer("Alice")
        assertTrue(player.id.isNotBlank())
        assertTrue(player.sessionToken.isNotBlank())
        assertTrue(player.sessionToken.length <= 64)
        assertEquals("Alice", player.displayName)
        assertTrue(player.createdAt > 0)
    }

    @Test
    fun `find player by token returns correct player`() {
        val created = repo.createPlayer("Bob")
        val found = repo.findByToken(created.sessionToken)
        assertNotNull(found)
        assertEquals(created.id, found.id)
        assertEquals("Bob", found.displayName)
    }

    @Test
    fun `find player by invalid token returns null`() {
        repo.createPlayer("Charlie")
        val found = repo.findByToken("invalid-token-that-does-not-exist")
        assertNull(found)
    }

    @Test
    fun `duplicate display names are allowed`() {
        val p1 = repo.createPlayer("SameName")
        val p2 = repo.createPlayer("SameName")
        assertNotEquals(p1.id, p2.id)
        assertNotEquals(p1.sessionToken, p2.sessionToken)
        assertEquals(p1.displayName, p2.displayName)
    }

    @Test
    fun `find player by id returns correct player`() {
        val created = repo.createPlayer("Diana")
        val found = repo.findById(created.id)
        assertNotNull(found)
        assertEquals(created.id, found.id)
        assertEquals("Diana", found.displayName)
    }

    @Test
    fun `find player by invalid id returns null`() {
        val found = repo.findById("nonexistent-id")
        assertNull(found)
    }
}
