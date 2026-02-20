package com.catan.db

import org.jetbrains.exposed.sql.Table

object PlayersTable : Table("players") {
    val id = varchar("id", 36)
    val displayName = varchar("display_name", 50)
    val sessionToken = varchar("session_token", 64)
    val createdAt = long("created_at")

    override val primaryKey = PrimaryKey(id)
}

object GamesTable : Table("games") {
    val id = varchar("id", 36)
    val status = varchar("status", 20)  // LOBBY, ACTIVE, FINISHED, ABANDONED
    val hostPlayerId = varchar("host_player_id", 36).references(PlayersTable.id)
    val maxPlayers = integer("max_players").default(4)
    val gameState = text("game_state").nullable()  // JSON-serialized GameState
    val winner = varchar("winner", 36).nullable()
    val createdAt = long("created_at")
    val updatedAt = long("updated_at")

    override val primaryKey = PrimaryKey(id)
}

object GamePlayersTable : Table("game_players") {
    val gameId = varchar("game_id", 36).references(GamesTable.id)
    val playerId = varchar("player_id", 36).references(PlayersTable.id)
    val color = varchar("color", 10)  // RED, BLUE, WHITE, ORANGE
    val seatIndex = integer("seat_index")

    override val primaryKey = PrimaryKey(gameId, playerId)
}

object GameActionsTable : Table("game_actions") {
    val id = integer("id").autoIncrement()
    val gameId = varchar("game_id", 36).references(GamesTable.id)
    val playerId = varchar("player_id", 36)
    val actionType = varchar("action_type", 50)
    val actionData = text("action_data")
    val timestamp = long("timestamp")

    override val primaryKey = PrimaryKey(id)
}
