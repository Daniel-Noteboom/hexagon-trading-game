# Catan Hexagon Trading Game

## Tech Stack
- **Backend:** Kotlin + Ktor (Netty), kotlinx.serialization, SQLite (via Exposed ORM)
- **Frontend:** React 18 + TypeScript, Vite, Zustand (state management)
- **Communication:** WebSocket-based real-time game state updates

## Project Structure
```
server/          Kotlin/Ktor backend
  src/main/kotlin/com/catan/
    game/        GameEngine, HexUtils, LongestRoadCalculator
    model/       Data models (GameState, GameAction, ServerEvent)
    ws/          WebSocket handler, session management
    routes/      REST API routes
    ai/          AI player strategies
    db/          Database repositories
client/          React/TypeScript frontend
  src/
    components/  UI components (board/, player/, trade/)
    services/    WebSocket client
    stores/      Zustand game store
    types/       TypeScript type definitions
    utils/       Hex math utilities
```

## Build & Run

### Backend
```bash
cd server
./gradlew run          # Start dev server on port 8080
./gradlew test         # Run tests
```

### Frontend
```bash
cd client
npm install
npm run dev            # Start Vite dev server
npm run typecheck      # TypeScript check (tsc --noEmit)
npm run test:e2e       # Playwright E2E tests
```

## Game State Flow
1. Client connects via WebSocket to `/games/{gameId}/ws?token={token}`
2. Server sends `GAME_STATE_UPDATE` with filtered state (hides other players' cards)
3. Client sends `GameAction` JSON messages (e.g., `ROLL_DICE`, `PLACE_SETTLEMENT`)
4. Server validates via `GameEngine.execute()`, persists state, broadcasts updates
5. Supplementary delta events (`DICE_ROLLED`, `BUILDING_PLACED`, etc.) sent alongside state updates
6. AI players are triggered automatically after each state change via `AiController`
