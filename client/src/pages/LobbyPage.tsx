import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { api } from '../services/api'
import type { GameInfoResponse } from '../services/api'
import { usePlayerStore } from '../stores/playerStore'

export function LobbyPage() {
  const [games, setGames] = useState<GameInfoResponse[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [selectedGame, setSelectedGame] = useState<string | null>(null)
  const navigate = useNavigate()
  const { playerId, displayName, sessionToken } = usePlayerStore()

  useEffect(() => {
    if (!playerId || !sessionToken) {
      navigate('/')
    }
  }, [playerId, sessionToken, navigate])

  const loadGames = async () => {
    try {
      const result = await api.listGames('LOBBY')
      setGames(result.games)
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to load games')
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    loadGames()
    const interval = setInterval(loadGames, 3000)
    return () => clearInterval(interval)
  }, [])

  const handleCreateGame = async () => {
    try {
      const result = await api.createGame()
      navigate(`/game/${result.gameId}`)
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to create game')
    }
  }

  if (!playerId || !sessionToken) return null

  const handleJoinGame = async (gameId: string) => {
    try {
      // Check if already in this game
      const game = games.find(g => g.gameId === gameId)
      const alreadyIn = game?.players.some(p => p.playerId === playerId)
      if (!alreadyIn) {
        await api.joinGame(gameId)
      }
      navigate(`/game/${gameId}`)
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to join game')
    }
  }

  return (
    <div style={styles.container}>
      <div style={styles.header}>
        <h1>Game Lobby</h1>
        <p style={styles.welcome}>Welcome, {displayName}</p>
      </div>

      <button onClick={handleCreateGame} style={styles.createBtn}>
        Create New Game
      </button>

      {error && <p style={styles.error}>{error}</p>}

      <h2 style={styles.sectionTitle}>Available Games</h2>

      {loading ? (
        <p>Loading games...</p>
      ) : games.length === 0 ? (
        <p style={styles.empty}>No games available. Create one!</p>
      ) : (
        <div style={styles.gameList}>
          {games.map((game) => (
            <div
              key={game.gameId}
              style={{
                ...styles.gameCard,
                ...(selectedGame === game.gameId ? styles.gameCardSelected : {}),
              }}
              onClick={() => setSelectedGame(game.gameId)}
            >
              <div style={styles.gameInfo}>
                <strong>Host: {game.players[0]?.displayName || 'Unknown'}</strong>
                <span style={styles.playerCount}>
                  {game.players.length}/{game.maxPlayers} players
                </span>
              </div>
              <div style={styles.playerList}>
                {game.players.map((p) => (
                  <span key={p.playerId} style={styles.playerBadge}>
                    {p.displayName}
                  </span>
                ))}
              </div>
              <button
                onClick={(e) => {
                  e.stopPropagation()
                  handleJoinGame(game.gameId)
                }}
                style={styles.joinBtn}
                disabled={game.players.length >= game.maxPlayers && !game.players.some(p => p.playerId === playerId)}
              >
                {game.players.some(p => p.playerId === playerId) ? 'Rejoin' : 'Join'}
              </button>
            </div>
          ))}
        </div>
      )}
    </div>
  )
}

const styles: Record<string, React.CSSProperties> = {
  container: { padding: '2rem', maxWidth: 700, margin: '0 auto' },
  header: { marginBottom: '1.5rem' },
  welcome: { color: '#7f8c8d' },
  createBtn: { padding: '0.75rem 1.5rem', fontSize: '1rem', borderRadius: 8, border: 'none', background: '#27ae60', color: '#fff', cursor: 'pointer', fontWeight: 'bold', marginBottom: '2rem' },
  sectionTitle: { marginBottom: '1rem', color: '#2c3e50' },
  empty: { color: '#95a5a6', fontStyle: 'italic' },
  gameList: { display: 'flex', flexDirection: 'column', gap: '0.75rem' },
  gameCard: { padding: '1rem', border: '2px solid #ecf0f1', borderRadius: 8, cursor: 'pointer', transition: 'border-color 0.2s' },
  gameCardSelected: { borderColor: '#3498db' },
  gameInfo: { display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '0.5rem' },
  playerCount: { color: '#7f8c8d', fontSize: '0.9rem' },
  playerList: { display: 'flex', gap: '0.5rem', flexWrap: 'wrap' as const, marginBottom: '0.5rem' },
  playerBadge: { background: '#ecf0f1', padding: '0.25rem 0.5rem', borderRadius: 4, fontSize: '0.85rem' },
  joinBtn: { padding: '0.5rem 1rem', borderRadius: 6, border: 'none', background: '#2980b9', color: '#fff', cursor: 'pointer' },
  error: { color: '#e74c3c', marginBottom: '1rem' },
}
