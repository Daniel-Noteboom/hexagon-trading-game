import { create } from 'zustand'

interface PlayerState {
  playerId: string | null
  sessionToken: string | null
  displayName: string | null
  setPlayer: (playerId: string, sessionToken: string, displayName: string) => void
  clearPlayer: () => void
}

export const usePlayerStore = create<PlayerState>((set) => {
  // Load from localStorage on init
  const stored = localStorage.getItem('catanPlayer')
  const initial = stored ? JSON.parse(stored) : { playerId: null, sessionToken: null, displayName: null }

  return {
    ...initial,
    setPlayer: (playerId, sessionToken, displayName) => {
      const data = { playerId, sessionToken, displayName }
      localStorage.setItem('catanPlayer', JSON.stringify(data))
      set(data)
    },
    clearPlayer: () => {
      localStorage.removeItem('catanPlayer')
      set({ playerId: null, sessionToken: null, displayName: null })
    },
  }
})
