import { Routes, Route, Navigate } from 'react-router-dom'
import { LandingPage } from './pages/LandingPage'
import { LobbyPage } from './pages/LobbyPage'
import { GamePage } from './pages/GamePage'

function App() {
  return (
    <Routes>
      <Route path="/" element={<LandingPage />} />
      <Route path="/lobby" element={<LobbyPage />} />
      <Route path="/game/:gameId" element={<GamePage />} />
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  )
}

export default App
