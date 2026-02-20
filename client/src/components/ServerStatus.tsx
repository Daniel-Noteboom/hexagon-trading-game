import type { HealthResponse } from '../api/health'

interface Props {
  health: HealthResponse | null
  error: string | null
  loading: boolean
}

export function ServerStatus({ health, error, loading }: Props) {
  if (loading) {
    return <p style={{ color: '#aaa' }}>Pinging backend...</p>
  }

  if (error) {
    return (
      <div style={{ padding: '1rem', background: '#ffe0e0', borderRadius: '8px', border: '1px solid #f99' }}>
        <strong>Backend unreachable</strong>
        <p style={{ margin: '0.5rem 0 0', fontSize: '0.875rem', color: '#c00' }}>{error}</p>
        <p style={{ margin: '0.5rem 0 0', fontSize: '0.75rem', color: '#888' }}>
          Is the Ktor server running? Try: <code>cd server && ./gradlew run</code>
        </p>
      </div>
    )
  }

  if (health) {
    return (
      <div style={{ padding: '1rem', background: '#e0ffe0', borderRadius: '8px', border: '1px solid #9f9' }}>
        <strong>Backend connected</strong>
        <ul style={{ margin: '0.5rem 0 0', paddingLeft: '1.25rem', fontSize: '0.875rem' }}>
          <li>Status: <code>{health.status}</code></li>
          <li>Service: <code>{health.service}</code></li>
          <li>Server time: <code>{new Date(health.timestamp).toLocaleTimeString()}</code></li>
        </ul>
      </div>
    )
  }

  return null
}
