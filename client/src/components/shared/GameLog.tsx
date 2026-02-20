import type { GameState } from '../../types/game'

interface Props {
  gameState: GameState
}

export function GameLog({ gameState }: Props) {
  const currentPlayer = gameState.players[gameState.currentPlayerIndex]
  const phase = gameState.phase
  const turnPhase = gameState.turnPhase

  const getStatusMessage = (): string => {
    if (phase === 'FINISHED') {
      return `${currentPlayer?.displayName} wins!`
    }
    if (phase === 'SETUP_FORWARD' || phase === 'SETUP_REVERSE') {
      const setup = gameState.setupState
      if (!setup.placedSettlement) {
        return `${currentPlayer?.displayName}: Place a settlement`
      }
      if (!setup.placedRoad) {
        return `${currentPlayer?.displayName}: Place a road`
      }
      return `${currentPlayer?.displayName}: Setup complete, waiting...`
    }
    if (phase === 'MAIN') {
      switch (turnPhase) {
        case 'ROLL_DICE':
          return `${currentPlayer?.displayName}'s turn: Roll dice`
        case 'ROBBER_MOVE':
          return `${currentPlayer?.displayName}: Move the robber`
        case 'ROBBER_STEAL':
          return `${currentPlayer?.displayName}: Steal a resource`
        case 'DISCARD':
          return `Players must discard cards`
        case 'TRADE_BUILD':
          return `${currentPlayer?.displayName}'s turn: Trade/Build`
        default:
          return `${currentPlayer?.displayName}'s turn`
      }
    }
    return ''
  }

  return (
    <div style={styles.container}>
      <div style={styles.title}>Game Status</div>
      <p style={styles.message}>{getStatusMessage()}</p>
      {gameState.longestRoadHolder && (
        <p style={styles.badge}>
          Longest Road: {gameState.players.find(p => p.id === gameState.longestRoadHolder)?.displayName}
        </p>
      )}
      {gameState.largestArmyHolder && (
        <p style={styles.badge}>
          Largest Army: {gameState.players.find(p => p.id === gameState.largestArmyHolder)?.displayName}
        </p>
      )}
      <p style={styles.info}>Dev Cards Left: {gameState.devCardDeck.length}</p>
    </div>
  )
}

const styles: Record<string, React.CSSProperties> = {
  container: { background: '#fff', borderRadius: 8, padding: '0.75rem', boxShadow: '0 1px 3px rgba(0,0,0,0.1)' },
  title: { fontWeight: 'bold', fontSize: '0.9rem', marginBottom: '0.25rem' },
  message: { fontSize: '0.85rem', color: '#2c3e50', margin: '0.25rem 0' },
  badge: { fontSize: '0.75rem', color: '#8e44ad', margin: '0.15rem 0' },
  info: { fontSize: '0.75rem', color: '#7f8c8d', margin: '0.15rem 0' },
}
