import type { EdgeCoord, HexCoord, ResourceType, VertexCoord } from './game'

export type GameAction =
  | { type: 'ROLL_DICE' }
  | { type: 'PLACE_SETTLEMENT'; vertex: VertexCoord }
  | { type: 'PLACE_CITY'; vertex: VertexCoord }
  | { type: 'PLACE_ROAD'; edge: EdgeCoord }
  | { type: 'MOVE_ROBBER'; hex: HexCoord }
  | { type: 'STEAL_RESOURCE'; targetPlayerId: string }
  | { type: 'DISCARD_RESOURCES'; resources: Partial<Record<ResourceType, number>> }
  | { type: 'OFFER_TRADE'; targetPlayerId?: string; offering: Partial<Record<ResourceType, number>>; requesting: Partial<Record<ResourceType, number>> }
  | { type: 'ACCEPT_TRADE' }
  | { type: 'DECLINE_TRADE' }
  | { type: 'BANK_TRADE'; giving: ResourceType; givingAmount: number; receiving: ResourceType }
  | { type: 'BUY_DEVELOPMENT_CARD' }
  | { type: 'PLAY_KNIGHT' }
  | { type: 'PLAY_ROAD_BUILDING' }
  | { type: 'PLAY_YEAR_OF_PLENTY'; resource1: ResourceType; resource2: ResourceType }
  | { type: 'PLAY_MONOPOLY'; resource: ResourceType }
  | { type: 'END_TURN' }
