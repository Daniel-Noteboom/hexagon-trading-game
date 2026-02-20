import { useState, useEffect } from 'react'
import { useNavigate } from 'react-router-dom'
import { api } from '../services/api'
import { usePlayerStore } from '../stores/playerStore'

export function LandingPage() {
  const [name, setName] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [loading, setLoading] = useState(false)
  const navigate = useNavigate()
  const { playerId, setPlayer } = usePlayerStore()

  // If already registered, go to lobby
  useEffect(() => {
    if (playerId) {
      navigate('/lobby')
    }
  }, [playerId, navigate])

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    if (!name.trim()) return

    setLoading(true)
    setError(null)
    try {
      const result = await api.register(name.trim())
      setPlayer(result.playerId, result.sessionToken, result.displayName)
      navigate('/lobby')
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Registration failed')
    } finally {
      setLoading(false)
    }
  }

  return (
    <div style={styles.container}>
      <h1 style={styles.title}>Settlers of Catan</h1>
      <p style={styles.subtitle}>Enter your name to start playing</p>

      <form onSubmit={handleSubmit} style={styles.form}>
        <input
          type="text"
          value={name}
          onChange={(e) => setName(e.target.value)}
          placeholder="Your display name"
          maxLength={50}
          style={styles.input}
          autoFocus
        />
        <button type="submit" disabled={loading || !name.trim()} style={styles.button}>
          {loading ? 'Registering...' : 'Enter Game'}
        </button>
      </form>

      {error && <p style={styles.error}>{error}</p>}
    </div>
  )
}

const styles: Record<string, React.CSSProperties> = {
  container: { textAlign: 'center', padding: '4rem 2rem', maxWidth: 400, margin: '0 auto' },
  title: { fontSize: '2.5rem', marginBottom: '0.5rem', color: '#2c3e50' },
  subtitle: { color: '#7f8c8d', marginBottom: '2rem' },
  form: { display: 'flex', flexDirection: 'column', gap: '1rem' },
  input: { padding: '0.75rem', fontSize: '1rem', borderRadius: 8, border: '2px solid #bdc3c7', outline: 'none' },
  button: { padding: '0.75rem', fontSize: '1rem', borderRadius: 8, border: 'none', background: '#2980b9', color: '#fff', cursor: 'pointer', fontWeight: 'bold' },
  error: { color: '#e74c3c', marginTop: '1rem' },
}
