import { useEffect, useState } from 'react'
import { useParams, useNavigate } from 'react-router-dom'
import { usePlayerStore } from '../stores/playerStore'
import { useGameStore } from '../stores/gameStore'
import { api } from '../services/api'
import { gameWebSocket } from '../services/websocket'
import type { GameInfoResponse } from '../services/api'
import { GameBoard } from '../components/board/GameBoard'
import { PlayerPanel } from '../components/player/PlayerPanel'
import { OpponentBar } from '../components/opponents/OpponentBar'
import { TradePanel } from '../components/trade/TradePanel'
import { DiceDisplay } from '../components/shared/DiceDisplay'
import { GameLog } from '../components/shared/GameLog'
import { VictoryBanner } from '../components/shared/VictoryBanner'
import { WaitingRoom } from '../components/lobby/WaitingRoom'

const PHASE_NAMES: Record<string, string> = {
  SETUP_FORWARD: 'Setup',
  SETUP_REVERSE: 'Setup',
  MAIN: 'Main',
  FINISHED: 'Game Over',
  LOBBY: 'Lobby',
}

const TURN_PHASE_NAMES: Record<string, string> = {
  ROLL_DICE: 'Roll Dice',
  ROBBER_MOVE: 'Move Robber',
  ROBBER_STEAL: 'Steal',
  DISCARD: 'Discard',
  TRADE_BUILD: 'Trade & Build',
  DONE: 'Done',
}

export function GamePage() {
  const { gameId } = useParams<{ gameId: string }>()
  const navigate = useNavigate()
  const { playerId, sessionToken } = usePlayerStore()
  const { gameState, connected, error, dismissError } = useGameStore()
  const [gameInfo, setGameInfo] = useState<GameInfoResponse | null>(null)
  const [loadError, setLoadError] = useState<string | null>(null)

  useEffect(() => {
    if (!playerId || !sessionToken) {
      navigate('/')
      return
    }
  }, [playerId, sessionToken, navigate])

  useEffect(() => {
    if (!gameId || !sessionToken) return

    // Load game info
    api.getGame(gameId)
      .then(setGameInfo)
      .catch((err) => setLoadError(err.message))

    // Connect WebSocket
    gameWebSocket.connect(gameId, sessionToken)

    // Also try to load state via REST as fallback
    api.getGameState(gameId)
      .then((state) => useGameStore.getState().setGameState(state))
      .catch(() => { /* Game might not be started yet */ })

    return () => {
      gameWebSocket.disconnect()
    }
  }, [gameId, sessionToken])

  // When game transitions from LOBBY to a started state, load the game state
  useEffect(() => {
    if (!gameId || !gameInfo || gameInfo.status === 'LOBBY' || gameState) return
    api.getGameState(gameId)
      .then((state) => useGameStore.getState().setGameState(state))
      .catch(() => { /* Will retry via WebSocket */ })
  }, [gameId, gameInfo, gameState])

  if (!playerId || !sessionToken) return null

  if (loadError) {
    return (
      <div style={styles.container}>
        <p style={styles.error}>Error: {loadError}</p>
        <button onClick={() => navigate('/lobby')} style={styles.backBtn}>Back to Lobby</button>
      </div>
    )
  }

  // Show waiting room if game hasn't started
  if (gameInfo && gameInfo.status === 'LOBBY' && !gameState) {
    return (
      <WaitingRoom
        gameId={gameId!}
        gameInfo={gameInfo}
        onGameStarted={() => {
          api.getGameState(gameId!)
            .then((state) => useGameStore.getState().setGameState(state))
        }}
        onRefresh={() => api.getGame(gameId!).then(setGameInfo)}
      />
    )
  }

  if (!gameState) {
    return <div style={styles.container}><p>Loading game...</p></div>
  }

  const myPlayer = gameState.players.find(p => p.id === playerId)
  const isMyTurn = gameState.players[gameState.currentPlayerIndex]?.id === playerId
  const winner = gameState.phase === 'FINISHED' ? gameState.players[gameState.currentPlayerIndex] : null

  return (
    <div style={styles.gamePage}>
      {winner && <VictoryBanner winner={winner} isMe={winner.id === playerId} />}

      <div style={styles.topBar}>
        <OpponentBar
          players={gameState.players}
          myPlayerId={playerId}
          currentPlayerIndex={gameState.currentPlayerIndex}
        />
        <div style={styles.statusBar}>
          <span style={styles.phase}>{PHASE_NAMES[gameState.phase] || gameState.phase} / {TURN_PHASE_NAMES[gameState.turnPhase] || gameState.turnPhase}</span>
          <span style={{ color: connected ? '#27ae60' : '#e74c3c' }}>
            {connected ? 'Connected' : 'Disconnected'}
          </span>
        </div>
      </div>

      {error && (
        <div style={styles.errorBanner} onClick={dismissError}>
          {error}
          <span style={styles.dismissHint}> (click to dismiss)</span>
        </div>
      )}

      <div style={styles.mainArea}>
        <div style={styles.boardContainer}>
          <GameBoard gameState={gameState} myPlayerId={playerId} isMyTurn={isMyTurn} />
        </div>

        <div style={styles.sidePanel}>
          {myPlayer && (
            <PlayerPanel
              player={myPlayer}
              gameState={gameState}
              isMyTurn={isMyTurn}
            />
          )}
          <DiceDisplay diceRoll={gameState.diceRoll} />
          <TradePanel gameState={gameState} myPlayerId={playerId} isMyTurn={isMyTurn} />
          <GameLog gameState={gameState} />
        </div>
      </div>
    </div>
  )
}

const styles: Record<string, React.CSSProperties> = {
  container: { padding: '2rem', textAlign: 'center' },
  error: { color: '#e74c3c' },
  backBtn: { marginTop: '1rem', padding: '0.5rem 1rem', borderRadius: 6, border: 'none', background: '#2980b9', color: '#fff', cursor: 'pointer' },
  gamePage: { height: '100vh', display: 'flex', flexDirection: 'column', overflow: 'hidden' },
  topBar: { padding: '0.5rem 1rem', background: '#2c3e50', color: '#fff', display: 'flex', justifyContent: 'space-between', alignItems: 'center' },
  statusBar: { display: 'flex', gap: '1rem', fontSize: '0.85rem' },
  phase: { color: '#bdc3c7' },
  errorBanner: { background: '#e74c3c', color: '#fff', padding: '0.5rem 1rem', textAlign: 'center', cursor: 'pointer' },
  dismissHint: { fontSize: '0.75rem', opacity: 0.7 },
  mainArea: { flex: 1, display: 'flex', overflow: 'hidden' },
  boardContainer: { flex: 1, display: 'flex', alignItems: 'center', justifyContent: 'center', background: '#1a5276', overflow: 'hidden' },
  sidePanel: { width: 320, overflowY: 'auto' as const, background: '#fafafa', borderLeft: '1px solid #ecf0f1', padding: '1rem' },
}
