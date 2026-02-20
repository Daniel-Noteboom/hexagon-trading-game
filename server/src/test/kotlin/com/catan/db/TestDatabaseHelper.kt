package com.catan.db

import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.transactions.transaction
import java.io.File

object TestDatabaseHelper {

    fun setupTestDatabase(): Database {
        // Use a temp file for each test so tests are isolated
        val tempFile = File.createTempFile("catan_test_", ".db")
        tempFile.deleteOnExit()
        val db = Database.connect("jdbc:sqlite:${tempFile.absolutePath}", driver = "org.sqlite.JDBC")
        TransactionManager.defaultDatabase = db
        transaction(db) {
            SchemaUtils.create(PlayersTable, GamesTable, GamePlayersTable, GameActionsTable)
        }
        return db
    }

    fun teardownTestDatabase(db: Database) {
        transaction(db) {
            SchemaUtils.drop(GameActionsTable, GamePlayersTable, GamesTable, PlayersTable)
        }
    }
}
