import type { Building, GameState, Road, TradeOffer } from './game'

export type ServerEvent =
  | { type: 'GAME_STATE_UPDATE'; state: GameState }
  | { type: 'DICE_ROLLED'; die1: number; die2: number; playerId: string }
  | { type: 'BUILDING_PLACED'; building: Building }
  | { type: 'ROAD_PLACED'; road: Road }
  | { type: 'TRADE_OFFERED'; trade: TradeOffer }
  | { type: 'TRADE_ACCEPTED'; acceptedBy: string; acceptedByName: string }
  | { type: 'TRADE_DECLINED'; declinedBy: string; declinedByName: string }
  | { type: 'TURN_CHANGED'; playerId: string; playerIndex: number }
  | { type: 'GAME_OVER'; winnerId: string; winnerName: string }
  | { type: 'ERROR'; message: string }
  | { type: 'PLAYER_JOINED'; playerId: string; displayName: string }
  | { type: 'GAME_STARTED'; state: GameState }
