import { useGameStore } from '../stores/gameStore'
import type { ServerEvent } from '../types/events'
import type { GameAction } from '../types/actions'

class GameWebSocket {
  private ws: WebSocket | null = null
  private gameId: string | null = null
  private token: string | null = null
  private reconnectTimer: ReturnType<typeof setTimeout> | null = null

  connect(gameId: string, token: string) {
    this.gameId = gameId
    this.token = token
    this.doConnect()
  }

  private doConnect() {
    if (!this.gameId || !this.token) return

    const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:'
    const host = window.location.hostname
    // In dev, WebSocket goes directly to backend port 8080
    const port = import.meta.env.DEV ? '8080' : window.location.port
    const url = `${protocol}//${host}:${port}/games/${this.gameId}/ws?token=${this.token}`

    this.ws = new WebSocket(url)

    this.ws.onopen = () => {
      useGameStore.getState().setConnected(true)
      useGameStore.getState().setError(null)
    }

    this.ws.onmessage = (event) => {
      try {
        const serverEvent: ServerEvent = JSON.parse(event.data)
        this.handleEvent(serverEvent)
      } catch (e) {
        console.error('Failed to parse WebSocket message:', e)
      }
    }

    this.ws.onclose = () => {
      useGameStore.getState().setConnected(false)
      // Auto-reconnect after 2 seconds
      this.reconnectTimer = setTimeout(() => this.doConnect(), 2000)
    }

    this.ws.onerror = () => {
      useGameStore.getState().setError('WebSocket connection error')
    }

    // Expose send function for E2E tests
    if (import.meta.env.DEV) {
      (window as any).__sendGameAction = (action: any) => this.send(action)
    }
  }

  private handleEvent(event: ServerEvent) {
    const store = useGameStore.getState()

    switch (event.type) {
      case 'GAME_STATE_UPDATE':
        store.setGameState(event.state)
        break
      case 'GAME_STARTED':
        store.setGameState(event.state)
        break
      case 'DICE_ROLLED':
        store.setLastDiceRoll([event.die1, event.die2])
        break
      case 'ERROR':
        store.setError(event.message)
        break
      // Delta events are informational; state update is the source of truth
      case 'BUILDING_PLACED':
      case 'ROAD_PLACED':
      case 'TRADE_OFFERED':
      case 'TURN_CHANGED':
      case 'PLAYER_JOINED':
        break
      case 'GAME_OVER':
        // State update will have FINISHED phase
        break
    }
  }

  send(action: GameAction) {
    if (this.ws?.readyState === WebSocket.OPEN) {
      this.ws.send(JSON.stringify(action))
    }
  }

  disconnect() {
    if (this.reconnectTimer) {
      clearTimeout(this.reconnectTimer)
      this.reconnectTimer = null
    }
    this.ws?.close()
    this.ws = null
    this.gameId = null
    this.token = null
    useGameStore.getState().clear()
  }
}

export const gameWebSocket = new GameWebSocket()
