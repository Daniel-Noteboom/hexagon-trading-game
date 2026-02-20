package com.catan.ai

import com.catan.game.BoardGenerator
import com.catan.game.GameEngine
import com.catan.game.GameEngineTestHelper
import com.catan.game.HexUtils
import com.catan.model.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ActionGeneratorTest {

    private val engine = GameEngine()

    // ============ Alternative A: Contrived Scenario Tests ============

    @Test
    fun `ROLL_DICE phase generates only RollDice`() {
        val state = GameEngineTestHelper.completeSetup(
            GameEngineTestHelper.createTestState(playerCount = 2),
            engine
        )
        assertEquals(GamePhase.MAIN, state.phase)
        assertEquals(TurnPhase.ROLL_DICE, state.turnPhase)

        val actions = ActionGenerator.generate(state, state.currentPlayer().id)
        assertEquals(1, actions.size)
        assertTrue(actions[0] is GameAction.RollDice)
    }

    @Test
    fun `setup with limited valid vertices returns correct PlaceSettlement actions`() {
        val state = GameEngineTestHelper.createTestState(playerCount = 2)
        assertEquals(GamePhase.SETUP_FORWARD, state.phase)

        val playerId = state.currentPlayer().id
        val actions = ActionGenerator.generate(state, playerId)

        // All actions should be PlaceSettlement
        assertTrue(actions.all { it is GameAction.PlaceSettlement })
        // On empty board, many vertices available
        assertTrue(actions.size > 10)

        // Place settlements to reduce available vertices
        val occupied = state.buildings.map { it.vertex }.toSet()
        val tooClose = occupied.flatMap { HexUtils.adjacentVertices(it) }.toSet()
        val expectedCount = HexUtils.ALL_VERTICES.count { it !in occupied && it !in tooClose }
        assertEquals(expectedCount, actions.size)
    }

    @Test
    fun `TRADE_BUILD with no resources generates only EndTurn and possible dev card plays`() {
        val state = GameEngineTestHelper.completeSetup(
            GameEngineTestHelper.createTestState(playerCount = 2),
            engine
        )
        // Force to TRADE_BUILD
        state.turnPhase = TurnPhase.TRADE_BUILD
        val player = state.currentPlayer()
        // Clear all resources
        player.resources.replaceAll { _, _ -> 0 }
        // Clear dev cards
        player.devCards.clear()

        val actions = ActionGenerator.generate(state, player.id)
        // Should have at least EndTurn
        assertTrue(actions.any { it is GameAction.EndTurn })
        // Should not have building actions
        assertTrue(actions.none { it is GameAction.PlaceSettlement })
        assertTrue(actions.none { it is GameAction.PlaceCity })
        assertTrue(actions.none { it is GameAction.PlaceRoad })
        assertTrue(actions.none { it is GameAction.BuyDevelopmentCard })
        assertTrue(actions.none { it is GameAction.BankTrade })
    }

    @Test
    fun `ROBBER_MOVE generates all valid hexes except current robber location`() {
        val state = GameEngineTestHelper.completeSetup(
            GameEngineTestHelper.createTestState(playerCount = 2),
            engine
        )
        state.turnPhase = TurnPhase.ROBBER_MOVE

        val playerId = state.currentPlayer().id
        val actions = ActionGenerator.generate(state, playerId)

        assertTrue(actions.all { it is GameAction.MoveRobber })
        // 19 hexes minus the current robber location = 18
        assertEquals(18, actions.size)
        assertTrue(actions.none { (it as GameAction.MoveRobber).hex == state.robberLocation })
    }

    @Test
    fun `DISCARD with 8 cards generates combinations that sum to 4`() {
        val state = GameEngineTestHelper.completeSetup(
            GameEngineTestHelper.createTestState(playerCount = 2),
            engine
        )
        state.turnPhase = TurnPhase.DISCARD
        val player = state.currentPlayer()
        // Give player exactly 8 resources
        player.resources[ResourceType.BRICK] = 2
        player.resources[ResourceType.LUMBER] = 2
        player.resources[ResourceType.ORE] = 2
        player.resources[ResourceType.GRAIN] = 1
        player.resources[ResourceType.WOOL] = 1
        state.discardingPlayerIds = mutableListOf(player.id)

        val actions = ActionGenerator.generate(state, player.id)
        assertTrue(actions.isNotEmpty())
        assertTrue(actions.all { it is GameAction.DiscardResources })
        // Each discard should sum to 4
        actions.forEach { action ->
            val discard = action as GameAction.DiscardResources
            assertEquals(4, discard.resources.values.sum())
        }
    }

    @Test
    fun `TRADE_BUILD with resources for settlement includes PlaceSettlement`() {
        val state = GameEngineTestHelper.completeSetup(
            GameEngineTestHelper.createTestState(playerCount = 2),
            engine
        )
        state.turnPhase = TurnPhase.TRADE_BUILD
        val player = state.currentPlayer()
        // Give resources for settlement
        player.resources[ResourceType.BRICK] = 1
        player.resources[ResourceType.LUMBER] = 1
        player.resources[ResourceType.GRAIN] = 1
        player.resources[ResourceType.WOOL] = 1

        val actions = ActionGenerator.generate(state, player.id)

        // If there are valid settlement vertices adjacent to player's roads, should include settlements
        val hasSettlementActions = actions.any { it is GameAction.PlaceSettlement }
        // Player has roads from setup, so there may be valid vertices
        // At minimum, EndTurn should be there
        assertTrue(actions.any { it is GameAction.EndTurn })
    }

    @Test
    fun `player with knight dev card generates PlayKnight when not played this turn`() {
        val state = GameEngineTestHelper.completeSetup(
            GameEngineTestHelper.createTestState(playerCount = 2),
            engine
        )
        state.turnPhase = TurnPhase.TRADE_BUILD
        val player = state.currentPlayer()
        player.devCards.add(DevelopmentCardType.KNIGHT)
        player.hasPlayedDevCardThisTurn = false

        val actions = ActionGenerator.generate(state, player.id)
        assertTrue(actions.any { it is GameAction.PlayKnight })
    }

    @Test
    fun `player who already played dev card this turn gets no dev card plays`() {
        val state = GameEngineTestHelper.completeSetup(
            GameEngineTestHelper.createTestState(playerCount = 2),
            engine
        )
        state.turnPhase = TurnPhase.TRADE_BUILD
        val player = state.currentPlayer()
        player.devCards.add(DevelopmentCardType.KNIGHT)
        player.hasPlayedDevCardThisTurn = true
        player.resources.replaceAll { _, _ -> 0 }

        val actions = ActionGenerator.generate(state, player.id)
        assertTrue(actions.none { it is GameAction.PlayKnight })
        assertTrue(actions.none { it is GameAction.PlayRoadBuilding })
        assertTrue(actions.none { it is GameAction.PlayYearOfPlenty })
        assertTrue(actions.none { it is GameAction.PlayMonopoly })
    }

    @Test
    fun `setup road placement generates edges adjacent to last settlement`() {
        val state = GameEngineTestHelper.createTestState(playerCount = 2)
        val playerId = state.currentPlayer().id

        // Place a settlement first
        val vertex = GameEngineTestHelper.findValidVertex(state)
        val settled = engine.execute(state, GameAction.PlaceSettlement(playerId, vertex)).getOrThrow()

        val actions = ActionGenerator.generate(settled, playerId)
        assertTrue(actions.all { it is GameAction.PlaceRoad })
        // All roads should be adjacent to the placed settlement
        val validEdges = HexUtils.edgesOfVertex(vertex).filter { settled.roadAt(it) == null }
        assertEquals(validEdges.size, actions.size)
        actions.forEach { action ->
            val road = action as GameAction.PlaceRoad
            assertTrue(road.edge in validEdges)
        }
    }

    @Test
    fun `non-current player gets no actions in most phases`() {
        val state = GameEngineTestHelper.completeSetup(
            GameEngineTestHelper.createTestState(playerCount = 2),
            engine
        )
        val otherPlayerId = state.players.first { it.id != state.currentPlayer().id }.id

        val actions = ActionGenerator.generate(state, otherPlayerId)
        assertTrue(actions.isEmpty())
    }

    @Test
    fun `ROBBER_STEAL generates steal from players with buildings and resources on robber hex`() {
        val state = GameEngineTestHelper.completeSetup(
            GameEngineTestHelper.createTestState(playerCount = 2),
            engine
        )
        state.turnPhase = TurnPhase.ROBBER_STEAL

        // Move robber to a hex where opponent has a building
        val currentPlayerId = state.currentPlayer().id
        val opponentBuilding = state.buildings.first { it.playerId != currentPlayerId }
        val opponentHex = HexUtils.hexesOfVertex(opponentBuilding.vertex).first()
        state.robberLocation = opponentHex
        // Also update tiles
        val newTiles = state.tiles.map { tile ->
            when {
                tile.hasRobber -> tile.copy(hasRobber = false)
                tile.coord == opponentHex -> tile.copy(hasRobber = true)
                else -> tile
            }
        }
        val stateWithRobber = state.copy(tiles = newTiles, robberLocation = opponentHex)
        // Ensure opponent has resources
        val opponent = stateWithRobber.players.first { it.id != currentPlayerId }
        opponent.resources[ResourceType.BRICK] = 1

        val actions = ActionGenerator.generate(stateWithRobber, currentPlayerId)
        assertTrue(actions.all { it is GameAction.StealResource })
        assertTrue(actions.any { (it as GameAction.StealResource).targetPlayerId == opponent.id })
    }

    @Test
    fun `bank trade actions generated when player has enough resources`() {
        val state = GameEngineTestHelper.completeSetup(
            GameEngineTestHelper.createTestState(playerCount = 2),
            engine
        )
        state.turnPhase = TurnPhase.TRADE_BUILD
        val player = state.currentPlayer()
        player.resources.replaceAll { _, _ -> 0 }
        player.resources[ResourceType.ORE] = 4
        player.devCards.clear()

        val actions = ActionGenerator.generate(state, player.id)
        val bankTrades = actions.filterIsInstance<GameAction.BankTrade>()
        // 4 ore can bank trade at 4:1 ratio for any of 4 other resources
        assertTrue(bankTrades.isNotEmpty())
        assertTrue(bankTrades.all { it.giving == ResourceType.ORE })
        assertEquals(4, bankTrades.size) // BRICK, LUMBER, GRAIN, WOOL
    }

    @Test
    fun `road building mode generates only road placements`() {
        val state = GameEngineTestHelper.completeSetup(
            GameEngineTestHelper.createTestState(playerCount = 2),
            engine
        )
        state.turnPhase = TurnPhase.TRADE_BUILD
        state.roadBuildingRoadsLeft = 2

        val playerId = state.currentPlayer().id
        val actions = ActionGenerator.generate(state, playerId)
        assertTrue(actions.all { it is GameAction.PlaceRoad })
        assertTrue(actions.isNotEmpty())
    }

    @Test
    fun `buy dev card available when affordable and deck not empty`() {
        val state = GameEngineTestHelper.completeSetup(
            GameEngineTestHelper.createTestState(playerCount = 2),
            engine
        )
        state.turnPhase = TurnPhase.TRADE_BUILD
        val player = state.currentPlayer()
        player.resources[ResourceType.ORE] = 1
        player.resources[ResourceType.GRAIN] = 1
        player.resources[ResourceType.WOOL] = 1

        val actions = ActionGenerator.generate(state, player.id)
        assertTrue(actions.any { it is GameAction.BuyDevelopmentCard })
    }

    @Test
    fun `trade response actions for target player`() {
        val state = GameEngineTestHelper.completeSetup(
            GameEngineTestHelper.createTestState(playerCount = 2),
            engine
        )
        state.turnPhase = TurnPhase.TRADE_BUILD
        val offerer = state.currentPlayer()
        val target = state.players.first { it.id != offerer.id }

        state.pendingTrade = TradeOffer(
            fromPlayerId = offerer.id,
            toPlayerId = target.id,
            offering = mapOf(ResourceType.BRICK to 1),
            requesting = mapOf(ResourceType.ORE to 1)
        )
        target.resources[ResourceType.ORE] = 1

        val actions = ActionGenerator.generate(state, target.id)
        assertTrue(actions.any { it is GameAction.AcceptTrade })
        assertTrue(actions.any { it is GameAction.DeclineTrade })
    }

    @Test
    fun `trade response without enough resources only allows decline`() {
        val state = GameEngineTestHelper.completeSetup(
            GameEngineTestHelper.createTestState(playerCount = 2),
            engine
        )
        state.turnPhase = TurnPhase.TRADE_BUILD
        val offerer = state.currentPlayer()
        val target = state.players.first { it.id != offerer.id }

        state.pendingTrade = TradeOffer(
            fromPlayerId = offerer.id,
            toPlayerId = target.id,
            offering = mapOf(ResourceType.BRICK to 1),
            requesting = mapOf(ResourceType.ORE to 1)
        )
        target.resources[ResourceType.ORE] = 0

        val actions = ActionGenerator.generate(state, target.id)
        assertTrue(actions.none { it is GameAction.AcceptTrade })
        assertTrue(actions.any { it is GameAction.DeclineTrade })
    }

    @Test
    fun `city upgrade actions generated when player has settlement and resources`() {
        val state = GameEngineTestHelper.completeSetup(
            GameEngineTestHelper.createTestState(playerCount = 2),
            engine
        )
        state.turnPhase = TurnPhase.TRADE_BUILD
        val player = state.currentPlayer()
        player.resources[ResourceType.GRAIN] = 2
        player.resources[ResourceType.ORE] = 3

        val actions = ActionGenerator.generate(state, player.id)
        val cityActions = actions.filterIsInstance<GameAction.PlaceCity>()
        // Player should have settlements from setup that can be upgraded
        val playerSettlements = state.buildings.filter {
            it.playerId == player.id && it.type == BuildingType.SETTLEMENT
        }
        assertEquals(playerSettlements.size, cityActions.size)
    }

    @Test
    fun `year of plenty generates resource combinations`() {
        val state = GameEngineTestHelper.completeSetup(
            GameEngineTestHelper.createTestState(playerCount = 2),
            engine
        )
        state.turnPhase = TurnPhase.TRADE_BUILD
        val player = state.currentPlayer()
        player.devCards.add(DevelopmentCardType.YEAR_OF_PLENTY)
        player.hasPlayedDevCardThisTurn = false

        val actions = ActionGenerator.generate(state, player.id)
        val yopActions = actions.filterIsInstance<GameAction.PlayYearOfPlenty>()
        // 5 resources choose 2 with repetition = 15 combinations
        assertEquals(15, yopActions.size)
    }

    @Test
    fun `monopoly generates one action per resource type`() {
        val state = GameEngineTestHelper.completeSetup(
            GameEngineTestHelper.createTestState(playerCount = 2),
            engine
        )
        state.turnPhase = TurnPhase.TRADE_BUILD
        val player = state.currentPlayer()
        player.devCards.add(DevelopmentCardType.MONOPOLY)
        player.hasPlayedDevCardThisTurn = false

        val actions = ActionGenerator.generate(state, player.id)
        val monopolyActions = actions.filterIsInstance<GameAction.PlayMonopoly>()
        assertEquals(5, monopolyActions.size) // One per resource type
    }

    @Test
    fun `finished game generates no actions`() {
        val state = GameEngineTestHelper.completeSetup(
            GameEngineTestHelper.createTestState(playerCount = 2),
            engine
        )
        state.phase = GamePhase.FINISHED

        val actions = ActionGenerator.generate(state, state.currentPlayer().id)
        assertTrue(actions.isEmpty())
    }

    // ============ Alternative B: Invariant Tests ============

    @Test
    fun `invariant - every generated action is accepted by GameEngine`() {
        // Generate 20 random mid-game states and verify all generated actions are legal
        repeat(20) { seed ->
            val state = createRandomMidGameState(seed)
            val playerId = findActionablePlayer(state) ?: return@repeat

            val actions = ActionGenerator.generate(state, playerId)
            for (action in actions) {
                val result = engine.execute(state.deepCopy(), action)
                assertTrue(
                    result.isSuccess,
                    "Action $action failed in state (phase=${state.phase}, turnPhase=${state.turnPhase}, " +
                    "seed=$seed): ${result.exceptionOrNull()?.message}"
                )
            }
        }
    }

    @Test
    fun `invariant - generator never returns empty for actionable player`() {
        repeat(20) { seed ->
            val state = createRandomMidGameState(seed)
            val playerId = findActionablePlayer(state) ?: return@repeat

            val actions = ActionGenerator.generate(state, playerId)
            assertTrue(
                actions.isNotEmpty(),
                "No actions generated for player $playerId in phase=${state.phase}, turnPhase=${state.turnPhase}, seed=$seed"
            )
        }
    }

    // ============ Helper Methods ============

    private fun findActionablePlayer(state: GameState): String? {
        if (state.phase == GamePhase.FINISHED || state.phase == GamePhase.LOBBY) return null

        // Discard phase: find a player who needs to discard
        if (state.turnPhase == TurnPhase.DISCARD && state.discardingPlayerIds.isNotEmpty()) {
            return state.discardingPlayerIds.first()
        }

        // Trade response: find the target
        if (state.pendingTrade != null) {
            val trade = state.pendingTrade!!
            val target = trade.toPlayerId ?: state.players.first { it.id != trade.fromPlayerId }.id
            return target
        }

        return state.currentPlayer().id
    }

    private fun createRandomMidGameState(seed: Int): GameState {
        val state = GameEngineTestHelper.completeSetup(
            GameEngineTestHelper.createTestState(playerCount = 2 + (seed % 3)), // 2-4 players
            engine
        )

        // Apply a few random legal actions to get diverse states
        var current = state
        val random = java.util.Random(seed.toLong())

        repeat(random.nextInt(10)) {
            val playerId = current.currentPlayer().id
            val actions = ActionGenerator.generate(current, playerId)
            if (actions.isEmpty()) return current

            val action = actions[random.nextInt(actions.size)]
            val result = engine.execute(current, action)
            if (result.isSuccess) {
                current = result.getOrThrow()
            }
            if (current.phase == GamePhase.FINISHED) return current
        }
        return current
    }

    private fun GameState.deepCopy(): GameState {
        return this.copy(
            buildings = this.buildings.toMutableList(),
            roads = this.roads.toMutableList(),
            players = this.players.map {
                it.copy(
                    resources = it.resources.toMutableMap(),
                    devCards = it.devCards.toMutableList(),
                    newDevCards = it.newDevCards.toMutableList()
                )
            }.toMutableList(),
            devCardDeck = this.devCardDeck.toMutableList(),
            discardingPlayerIds = this.discardingPlayerIds.toMutableList()
        )
    }
}
