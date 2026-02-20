export type ResourceType = 'BRICK' | 'LUMBER' | 'ORE' | 'GRAIN' | 'WOOL'
export type TileType = 'HILLS' | 'FOREST' | 'MOUNTAINS' | 'FIELDS' | 'PASTURE' | 'DESERT'
export type PlayerColor = 'RED' | 'BLUE' | 'WHITE' | 'ORANGE'
export type BuildingType = 'SETTLEMENT' | 'CITY'
export type GamePhase = 'LOBBY' | 'SETUP_FORWARD' | 'SETUP_REVERSE' | 'MAIN' | 'FINISHED'
export type TurnPhase = 'ROLL_DICE' | 'ROBBER_MOVE' | 'ROBBER_STEAL' | 'DISCARD' | 'TRADE_BUILD' | 'DONE'
export type VertexDirection = 'N' | 'S'
export type EdgeDirection = 'NE' | 'E' | 'SE'
export type DevelopmentCardType = 'KNIGHT' | 'VICTORY_POINT' | 'ROAD_BUILDING' | 'YEAR_OF_PLENTY' | 'MONOPOLY'
export type PortType = 'GENERIC_3_1' | 'BRICK_2_1' | 'LUMBER_2_1' | 'ORE_2_1' | 'GRAIN_2_1' | 'WOOL_2_1'

export interface HexCoord {
  q: number
  r: number
}

export interface VertexCoord {
  q: number
  r: number
  dir: VertexDirection
}

export interface EdgeCoord {
  q: number
  r: number
  dir: EdgeDirection
}

export interface HexTile {
  coord: HexCoord
  tileType: TileType
  diceNumber: number | null
  hasRobber: boolean
}

export interface Port {
  vertices: [VertexCoord, VertexCoord]
  portType: PortType
}

export interface Player {
  id: string
  displayName: string
  color: PlayerColor
  resources: Record<ResourceType, number>
  devCards: DevelopmentCardType[]
  newDevCards: DevelopmentCardType[]
  knightsPlayed: number
  victoryPoints: number
  hasPlayedDevCardThisTurn: boolean
}

export interface Building {
  vertex: VertexCoord
  playerId: string
  type: BuildingType
}

export interface Road {
  edge: EdgeCoord
  playerId: string
}

export interface TradeOffer {
  fromPlayerId: string
  toPlayerId: string | null
  offering: Partial<Record<ResourceType, number>>
  requesting: Partial<Record<ResourceType, number>>
  id: string
}

export interface SetupState {
  placedSettlement: boolean
  placedRoad: boolean
  lastSettlementVertex: VertexCoord | null
}

export interface GameState {
  gameId: string
  tiles: HexTile[]
  ports: Port[]
  buildings: Building[]
  roads: Road[]
  players: Player[]
  currentPlayerIndex: number
  phase: GamePhase
  turnPhase: TurnPhase
  robberLocation: HexCoord
  longestRoadHolder: string | null
  largestArmyHolder: string | null
  devCardDeck: DevelopmentCardType[]
  diceRoll: [number, number] | null
  pendingTrade: TradeOffer | null
  setupState: SetupState
  discardingPlayerIds: string[]
  roadBuildingRoadsLeft: number
}

// Resource colors for UI
export const RESOURCE_COLORS: Record<ResourceType, string> = {
  BRICK: '#c45a34',
  LUMBER: '#2d5a27',
  ORE: '#6b6b6b',
  GRAIN: '#d4a017',
  WOOL: '#8fbc8f',
}

export const TILE_COLORS: Record<TileType, string> = {
  HILLS: '#c45a34',
  FOREST: '#2d5a27',
  MOUNTAINS: '#6b6b6b',
  FIELDS: '#d4a017',
  PASTURE: '#8fbc8f',
  DESERT: '#e8d5a3',
}

export const PLAYER_COLORS: Record<PlayerColor, string> = {
  RED: '#e74c3c',
  BLUE: '#3498db',
  WHITE: '#ecf0f1',
  ORANGE: '#e67e22',
}

export const RESOURCE_NAMES: Record<ResourceType, string> = {
  BRICK: 'Brick',
  LUMBER: 'Lumber',
  ORE: 'Ore',
  GRAIN: 'Grain',
  WOOL: 'Wool',
}

export const BUILDING_COSTS = {
  ROAD: { BRICK: 1, LUMBER: 1 } as Partial<Record<ResourceType, number>>,
  SETTLEMENT: { BRICK: 1, LUMBER: 1, GRAIN: 1, WOOL: 1 } as Partial<Record<ResourceType, number>>,
  CITY: { GRAIN: 2, ORE: 3 } as Partial<Record<ResourceType, number>>,
  DEV_CARD: { ORE: 1, GRAIN: 1, WOOL: 1 } as Partial<Record<ResourceType, number>>,
}
