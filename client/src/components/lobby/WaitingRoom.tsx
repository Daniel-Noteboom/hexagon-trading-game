import { useEffect, useState } from 'react'
import { usePlayerStore } from '../../stores/playerStore'
import { api } from '../../services/api'
import type { GameInfoResponse } from '../../services/api'

interface Props {
  gameId: string
  gameInfo: GameInfoResponse
  onGameStarted: () => void
  onRefresh: () => void
}

export function WaitingRoom({ gameId, gameInfo, onGameStarted, onRefresh }: Props) {
  const { playerId } = usePlayerStore()
  const isHost = gameInfo.hostPlayerId === playerId
  const [aiDifficulty, setAiDifficulty] = useState<string>('MEDIUM')
  const [addingAi, setAddingAi] = useState(false)

  useEffect(() => {
    const interval = setInterval(onRefresh, 2000)
    return () => clearInterval(interval)
  }, [onRefresh])

  const handleStart = async () => {
    try {
      await api.startGame(gameId)
      onGameStarted()
    } catch (err) {
      alert(err instanceof Error ? err.message : 'Failed to start game')
    }
  }

  const handleAddAi = async () => {
    setAddingAi(true)
    try {
      await api.addAiPlayer(gameId, aiDifficulty)
      onRefresh()
    } catch (err) {
      alert(err instanceof Error ? err.message : 'Failed to add AI player')
    } finally {
      setAddingAi(false)
    }
  }

  const COLORS: Record<string, string> = {
    RED: '#e74c3c',
    BLUE: '#3498db',
    WHITE: '#ecf0f1',
    ORANGE: '#e67e22',
  }

  const isFull = gameInfo.players.length >= gameInfo.maxPlayers

  return (
    <div style={styles.container}>
      <h2 style={styles.title}>Waiting Room</h2>
      <p style={styles.subtitle}>
        {gameInfo.players.length}/{gameInfo.maxPlayers} players joined
      </p>

      <div style={styles.playerList}>
        {gameInfo.players.map((p) => (
          <div key={p.playerId} style={styles.playerCard}>
            <div style={{ ...styles.colorDot, background: COLORS[p.color] || '#999' }} />
            <span style={styles.playerName}>
              {p.displayName}
              {p.playerId === gameInfo.hostPlayerId && <span style={styles.hostBadge}>Host</span>}
              {p.playerId === playerId && <span style={styles.youBadge}>You</span>}
              {p.isAi && <span style={styles.aiBadge}>AI {p.aiDifficulty}</span>}
            </span>
          </div>
        ))}
        {Array.from({ length: gameInfo.maxPlayers - gameInfo.players.length }).map((_, i) => (
          <div key={`empty-${i}`} style={styles.emptySlot}>
            Waiting for player...
          </div>
        ))}
      </div>

      {isHost && !isFull && (
        <div style={styles.aiSection}>
          <select
            value={aiDifficulty}
            onChange={(e) => setAiDifficulty(e.target.value)}
            style={styles.difficultySelect}
          >
            <option value="EASY">Easy</option>
            <option value="MEDIUM">Medium</option>
            <option value="HARD">Hard</option>
          </select>
          <button
            onClick={handleAddAi}
            style={styles.addAiBtn}
            disabled={addingAi}
          >
            {addingAi ? 'Adding...' : 'Add AI Player'}
          </button>
        </div>
      )}

      {isHost && (
        <button
          onClick={handleStart}
          style={{
            ...styles.startBtn,
            opacity: gameInfo.players.length >= 2 ? 1 : 0.5,
          }}
          disabled={gameInfo.players.length < 2}
        >
          Start Game ({gameInfo.players.length} players)
        </button>
      )}

      {!isHost && (
        <p style={styles.waitMsg}>Waiting for host to start the game...</p>
      )}
    </div>
  )
}

const styles: Record<string, React.CSSProperties> = {
  container: { padding: '2rem', maxWidth: 500, margin: '0 auto', textAlign: 'center' },
  title: { fontSize: '1.5rem', marginBottom: '0.5rem', color: '#2c3e50' },
  subtitle: { color: '#7f8c8d', marginBottom: '1.5rem' },
  playerList: { display: 'flex', flexDirection: 'column', gap: '0.5rem', marginBottom: '1.5rem' },
  playerCard: { display: 'flex', alignItems: 'center', gap: '0.75rem', padding: '0.75rem 1rem', background: '#fff', borderRadius: 8, border: '1px solid #ecf0f1' },
  colorDot: { width: 16, height: 16, borderRadius: '50%', border: '2px solid #333' },
  playerName: { fontWeight: 'bold', display: 'flex', alignItems: 'center', gap: '0.5rem' },
  hostBadge: { fontSize: '0.7rem', background: '#f1c40f', color: '#333', padding: '0.1rem 0.4rem', borderRadius: 4, fontWeight: 'normal' },
  youBadge: { fontSize: '0.7rem', background: '#3498db', color: '#fff', padding: '0.1rem 0.4rem', borderRadius: 4, fontWeight: 'normal' },
  aiBadge: { fontSize: '0.7rem', background: '#9b59b6', color: '#fff', padding: '0.1rem 0.4rem', borderRadius: 4, fontWeight: 'normal' },
  emptySlot: { padding: '0.75rem 1rem', background: '#f8f9fa', borderRadius: 8, border: '2px dashed #bdc3c7', color: '#95a5a6', fontStyle: 'italic' },
  aiSection: { display: 'flex', gap: '0.5rem', justifyContent: 'center', marginBottom: '1rem' },
  difficultySelect: { padding: '0.5rem', borderRadius: 6, border: '1px solid #bdc3c7', fontSize: '0.9rem' },
  addAiBtn: { padding: '0.5rem 1rem', borderRadius: 6, border: 'none', background: '#9b59b6', color: '#fff', cursor: 'pointer', fontWeight: 'bold', fontSize: '0.9rem' },
  startBtn: { padding: '0.75rem 2rem', fontSize: '1rem', borderRadius: 8, border: 'none', background: '#27ae60', color: '#fff', cursor: 'pointer', fontWeight: 'bold' },
  waitMsg: { color: '#7f8c8d', fontStyle: 'italic' },
}
