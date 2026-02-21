package com.catan.plugins

import com.catan.db.*
import io.ktor.server.application.*
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction

fun Application.configureDatabase() {
    val dbPath = environment.config.propertyOrNull("database.path")?.getString() ?: "./catan.db"
    Database.connect(url = "jdbc:sqlite:$dbPath", driver = "org.sqlite.JDBC")
    transaction {
        SchemaUtils.createMissingTablesAndColumns(PlayersTable, GamesTable, GamePlayersTable, GameActionsTable)
    }
    log.info("Database connected: jdbc:sqlite:$dbPath")
}
