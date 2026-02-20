const BASE_URL = '/api'

function getToken(): string | null {
  const stored = localStorage.getItem('catanPlayer')
  if (!stored) return null
  return JSON.parse(stored).sessionToken
}

async function request<T>(path: string, options: RequestInit = {}): Promise<T> {
  const token = getToken()
  const headers: Record<string, string> = {
    'Content-Type': 'application/json',
    ...(options.headers as Record<string, string> || {}),
  }
  if (token) {
    headers['X-Session-Token'] = token
  }

  const response = await fetch(`${BASE_URL}${path}`, { ...options, headers })
  if (!response.ok) {
    const error = await response.json().catch(() => ({ error: response.statusText }))
    throw new Error(error.error || error.message || response.statusText)
  }
  return response.json()
}

export interface RegisterResponse {
  playerId: string
  sessionToken: string
  displayName: string
}

export interface CreateGameResponse {
  gameId: string
}

export interface GamePlayerInfo {
  playerId: string
  displayName: string
  color: string
  seatIndex: number
}

export interface GameInfoResponse {
  gameId: string
  status: string
  hostPlayerId: string
  maxPlayers: number
  players: GamePlayerInfo[]
}

export interface GameListResponse {
  games: GameInfoResponse[]
}

export interface JoinGameResponse {
  color: string
  seatIndex: number
}

export const api = {
  register: (displayName: string) =>
    request<RegisterResponse>('/players/register', {
      method: 'POST',
      body: JSON.stringify({ displayName }),
    }),

  createGame: (maxPlayers = 4) =>
    request<CreateGameResponse>('/games', {
      method: 'POST',
      body: JSON.stringify({ maxPlayers }),
    }),

  listGames: (status?: string) =>
    request<GameListResponse>(status ? `/games?status=${status}` : '/games'),

  getGame: (gameId: string) =>
    request<GameInfoResponse>(`/games/${gameId}`),

  joinGame: (gameId: string) =>
    request<JoinGameResponse>(`/games/${gameId}/join`, { method: 'POST' }),

  startGame: (gameId: string) =>
    request<{ success: boolean }>(`/games/${gameId}/start`, { method: 'POST' }),

  getGameState: (gameId: string) =>
    request<import('../types/game').GameState>(`/games/${gameId}/state`),
}
