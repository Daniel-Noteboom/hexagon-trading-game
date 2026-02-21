import { create } from 'zustand'
import type { GameState, Player, Port } from '../types/game'

const DEFAULT_RESOURCES = { BRICK: 0, LUMBER: 0, ORE: 0, GRAIN: 0, WOOL: 0 } as const

/**
 * Normalize the game state from the backend to fill in any missing fields
 * with safe defaults. The backend may omit fields that are empty/zero.
 */
function normalizeGameState(raw: any): GameState {
  return {
    gameId: raw.gameId ?? '',
    tiles: (raw.tiles ?? []).map((t: any) => ({
      ...t,
      hasRobber: t.hasRobber ?? false,
    })),
    ports: (raw.ports ?? []).map((p: any): Port => ({
      portType: p.portType,
      vertices: Array.isArray(p.vertices)
        ? p.vertices
        : [p.vertices?.first, p.vertices?.second],
    })),
    buildings: raw.buildings ?? [],
    roads: raw.roads ?? [],
    players: (raw.players ?? []).map((p: any): Player => ({
      id: p.id ?? '',
      displayName: p.displayName ?? '',
      color: p.color ?? 'RED',
      resources: { ...DEFAULT_RESOURCES, ...p.resources },
      devCards: p.devCards ?? [],
      newDevCards: p.newDevCards ?? [],
      knightsPlayed: p.knightsPlayed ?? 0,
      victoryPoints: p.victoryPoints ?? 0,
      hasPlayedDevCardThisTurn: p.hasPlayedDevCardThisTurn ?? false,
      isAi: p.isAi ?? false,
      aiDifficulty: p.aiDifficulty ?? null,
    })),
    currentPlayerIndex: raw.currentPlayerIndex ?? 0,
    phase: raw.phase ?? 'SETUP_FORWARD',
    turnPhase: raw.turnPhase ?? 'ROLL_DICE',
    robberLocation: raw.robberLocation ?? { q: 0, r: 0 },
    longestRoadHolder: raw.longestRoadHolder ?? null,
    largestArmyHolder: raw.largestArmyHolder ?? null,
    devCardDeck: raw.devCardDeck ?? [],
    diceRoll: raw.diceRoll
      ? (Array.isArray(raw.diceRoll) ? raw.diceRoll : [raw.diceRoll.first, raw.diceRoll.second])
      : null,
    pendingTrade: raw.pendingTrade ?? null,
    setupState: raw.setupState ?? { placedSettlement: false, placedRoad: false, lastSettlementVertex: null },
    discardingPlayerIds: raw.discardingPlayerIds ?? [],
    roadBuildingRoadsLeft: raw.roadBuildingRoadsLeft ?? 0,
  }
}

export interface TradeResult {
  type: 'accepted' | 'declined'
  playerName: string
}

interface GameStore {
  gameState: GameState | null
  connected: boolean
  error: string | null
  lastDiceRoll: [number, number] | null
  tradeResult: TradeResult | null
  setGameState: (state: GameState) => void
  setConnected: (connected: boolean) => void
  setError: (error: string | null) => void
  dismissError: () => void
  setLastDiceRoll: (roll: [number, number] | null) => void
  setTradeResult: (result: TradeResult | null) => void
  clear: () => void
}

export const useGameStore = create<GameStore>((set) => ({
  gameState: null,
  connected: false,
  error: null,
  lastDiceRoll: null,
  tradeResult: null,
  setGameState: (state) => set({ gameState: normalizeGameState(state) }),
  setConnected: (connected) => set({ connected }),
  setError: (error) => {
    set({ error })
    if (error) {
      setTimeout(() => {
        if (useGameStore.getState().error === error) set({ error: null })
      }, 5000)
    }
  },
  dismissError: () => set({ error: null }),
  setLastDiceRoll: (roll) => set({ lastDiceRoll: roll }),
  setTradeResult: (result) => {
    set({ tradeResult: result })
    if (result) {
      setTimeout(() => {
        if (useGameStore.getState().tradeResult === result) set({ tradeResult: null })
      }, 4000)
    }
  },
  clear: () => set({ gameState: null, connected: false, error: null, lastDiceRoll: null, tradeResult: null }),
}))
