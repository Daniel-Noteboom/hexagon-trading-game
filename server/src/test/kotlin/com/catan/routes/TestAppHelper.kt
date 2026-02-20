package com.catan.routes

import com.catan.db.*
import com.catan.plugins.*
import io.ktor.client.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.transactions.transaction
import java.io.File

/**
 * Sets up a test Ktor application with all plugins and routes wired to a fresh test database.
 */
fun testApp(block: suspend ApplicationTestBuilder.(HttpClient, PlayerRepository, GameRepository) -> Unit) {
    val tempFile = File.createTempFile("catan_route_test_", ".db")
    tempFile.deleteOnExit()
    val db = Database.connect("jdbc:sqlite:${tempFile.absolutePath}", driver = "org.sqlite.JDBC")
    TransactionManager.defaultDatabase = db
    transaction(db) {
        SchemaUtils.create(PlayersTable, GamesTable, GamePlayersTable, GameActionsTable)
    }

    val playerRepo = PlayerRepository()
    val gameRepo = GameRepository()

    testApplication {
        application {
            configureSerialization()
            configureStatusPages()
            routing {
                healthRoutes()
                playerRoutes(playerRepo)
                gameRoutes(gameRepo, playerRepo)
            }
        }

        val client = createClient {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true; prettyPrint = true })
            }
        }

        block(client, playerRepo, gameRepo)
    }

    transaction(db) {
        SchemaUtils.drop(GameActionsTable, GamePlayersTable, GamesTable, PlayersTable)
    }
}
