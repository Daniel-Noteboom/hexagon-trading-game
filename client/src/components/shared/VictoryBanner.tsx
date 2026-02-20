import type { Player } from '../../types/game'
import { PLAYER_COLORS } from '../../types/game'

interface Props {
  winner: Player
  isMe: boolean
}

export function VictoryBanner({ winner, isMe }: Props) {
  return (
    <div style={styles.overlay}>
      <div style={styles.banner}>
        <h2 style={styles.title}>
          {isMe ? 'You Win!' : `${winner.displayName} Wins!`}
        </h2>
        <p style={styles.subtitle}>
          {winner.victoryPoints} Victory Points
        </p>
        <div style={{ ...styles.colorBar, background: PLAYER_COLORS[winner.color] }} />
      </div>
    </div>
  )
}

const styles: Record<string, React.CSSProperties> = {
  overlay: { position: 'fixed', top: 0, left: 0, right: 0, bottom: 0, background: 'rgba(0,0,0,0.6)', display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 1000 },
  banner: { background: '#fff', borderRadius: 16, padding: '2rem 3rem', textAlign: 'center', boxShadow: '0 8px 32px rgba(0,0,0,0.3)' },
  title: { fontSize: '2rem', margin: '0 0 0.5rem', color: '#2c3e50' },
  subtitle: { fontSize: '1.2rem', color: '#7f8c8d', margin: 0 },
  colorBar: { height: 6, borderRadius: 3, marginTop: '1rem' },
}
