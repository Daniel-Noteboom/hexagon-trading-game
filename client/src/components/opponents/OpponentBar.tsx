import type { Player, PlayerColor } from '../../types/game'
import { PLAYER_COLORS } from '../../types/game'

interface Props {
  players: Player[]
  myPlayerId: string
  currentPlayerIndex: number
}

export function OpponentBar({ players, myPlayerId, currentPlayerIndex }: Props) {
  const opponents = players.filter(p => p.id !== myPlayerId)

  return (
    <div style={styles.bar}>
      {opponents.map((p) => {
        const isCurrent = players[currentPlayerIndex]?.id === p.id
        const totalResources = Object.values(p.resources).reduce((a, b) => a + b, 0)

        return (
          <div key={p.id} style={{ ...styles.card, ...(isCurrent ? styles.currentCard : {}) }}>
            <div style={styles.nameRow}>
              <span style={{ ...styles.dot, background: PLAYER_COLORS[p.color as PlayerColor] }} />
              <span style={styles.name}>{p.displayName}</span>
              {p.isAi && <span style={styles.aiBadge}>AI</span>}
            </div>
            <div style={styles.stats}>
              <span title="Victory Points" style={styles.stat}>{p.victoryPoints} VP</span>
              <span title="Resource cards" style={styles.stat}>{totalResources} cards</span>
              <span title="Dev cards" style={styles.stat}>{p.devCards.length} dev</span>
              {p.knightsPlayed > 0 && <span title="Knights played" style={styles.stat}>{p.knightsPlayed} knights</span>}
            </div>
          </div>
        )
      })}
    </div>
  )
}

const styles: Record<string, React.CSSProperties> = {
  bar: { display: 'flex', gap: '0.5rem', flexWrap: 'wrap' as const },
  card: { padding: '0.35rem 0.6rem', background: 'rgba(255,255,255,0.1)', borderRadius: 6, fontSize: '0.8rem', border: '2px solid transparent' },
  currentCard: { borderColor: '#f1c40f' },
  nameRow: { display: 'flex', alignItems: 'center', gap: '0.3rem', marginBottom: 2 },
  dot: { width: 8, height: 8, borderRadius: '50%', display: 'inline-block' },
  name: { fontWeight: 'bold' },
  aiBadge: { fontSize: '0.6rem', background: '#9b59b6', color: '#fff', padding: '0.1rem 0.3rem', borderRadius: 3, fontWeight: 'normal' },
  stats: { display: 'flex', gap: '0.5rem', fontSize: '0.7rem', color: '#bdc3c7' },
  stat: {},
}
