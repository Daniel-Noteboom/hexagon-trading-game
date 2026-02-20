interface Props {
  diceRoll: [number, number] | null
}

export function DiceDisplay({ diceRoll }: Props) {
  if (!diceRoll) return null

  const [die1, die2] = diceRoll
  const total = die1 + die2

  return (
    <div style={styles.container}>
      <div style={styles.dice}>
        <div style={styles.die}>{die1}</div>
        <div style={styles.die}>{die2}</div>
      </div>
      <span style={styles.total}>= {total}</span>
    </div>
  )
}

const styles: Record<string, React.CSSProperties> = {
  container: { display: 'flex', alignItems: 'center', gap: '0.5rem', padding: '0.5rem 0.75rem', background: '#fff', borderRadius: 8, marginBottom: '0.75rem', boxShadow: '0 1px 3px rgba(0,0,0,0.1)' },
  dice: { display: 'flex', gap: '0.3rem' },
  die: { width: 36, height: 36, borderRadius: 6, border: '2px solid #333', display: 'flex', alignItems: 'center', justifyContent: 'center', fontWeight: 'bold', fontSize: '1.1rem', background: '#fff' },
  total: { fontWeight: 'bold', fontSize: '1rem', color: '#2c3e50' },
}
