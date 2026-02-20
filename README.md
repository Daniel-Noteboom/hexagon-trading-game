# Hexagon Trading Game

A fully playable Catan-style board game built in one evening using [Claude Code](https://claude.ai/code). Features real-time multiplayer via WebSockets, a complete game engine with rules enforcement, and an interactive hex-grid board rendered in SVG.

The main functionality is working end-to-end: players can create/join games, go through setup placement, roll dice, collect resources, build roads/settlements/cities, trade with other players or the bank, play development cards, and win by reaching 10 victory points. The graphics are basic/functional — inline SVG with flat colors rather than polished game art.

## Architecture

### Backend (Kotlin/Ktor)

```
server/src/main/kotlin/com/catan/
├── Application.kt              # Ktor entry point
├── db/                         # SQLite persistence via Exposed ORM
│   ├── GameRepository.kt
│   ├── PlayerRepository.kt
│   └── Tables.kt
├── game/                       # Core game logic (pure, no I/O)
│   ├── GameEngine.kt           # Action dispatch + rules enforcement
│   ├── HexUtils.kt             # Hex grid math (axial coordinates)
│   ├── BoardGenerator.kt       # Random board setup
│   └── LongestRoadCalculator.kt
├── model/                      # Data classes (GameState, Player, etc.)
├── plugins/                    # Ktor plugins (CORS, serialization, etc.)
├── routes/                     # REST API endpoints
└── ws/                         # WebSocket session management
    ├── GameSessionManager.kt
    └── GameWebSocketHandler.kt
```

- **Game engine** is a pure function: `execute(state, action) -> Result<GameState>`. All Catan rules (distance rule, road connectivity, robber, dev cards, longest road, largest army, supply limits) are enforced server-side.
- **Real-time sync** via WebSocket — clients send actions, server broadcasts updated game state to all players in the session.
- **Persistence** with SQLite through Exposed ORM for game and player data.

### Frontend (React/TypeScript/Vite)

```
client/src/
├── components/
│   ├── board/
│   │   ├── GameBoard.tsx       # SVG hex board with click targets
│   │   └── hexLayout.ts       # Hex-to-pixel coordinate math
│   ├── player/PlayerPanel.tsx  # Resources, actions, dev cards
│   ├── trade/TradePanel.tsx    # Player and bank trading
│   ├── opponents/OpponentBar.tsx
│   ├── lobby/WaitingRoom.tsx
│   └── shared/                 # Dice, game log, victory banner
├── pages/                      # GamePage, LobbyPage, LandingPage
├── stores/                     # Zustand state management
├── services/                   # REST API client + WebSocket client
├── utils/hexUtils.ts           # Client-side hex adjacency math
└── types/                      # TypeScript interfaces matching backend
```

- **Board rendering**: Flat-top hexagons in SVG using axial coordinates. Click targets for vertices and edges are filtered client-side to only show valid placements (distance rule, road connectivity).
- **State management**: Zustand store receives game state via WebSocket and drives all UI reactively.
- **Vite dev proxy**: `/api/*` requests are proxied to the Ktor backend at `localhost:8080`.

## Testing

The backend has **128 unit tests** covering:

- **Game engine**: Setup placement, main-phase building, dice/resource distribution, robber mechanics, development cards (knight, road building, year of plenty, monopoly), trading (player-to-player and bank), victory conditions, longest road/largest army
- **Hex utilities**: Vertex/edge adjacency, coordinate math
- **Board generator**: Valid Catan board generation
- **Database**: Repository CRUD operations
- **Routes**: REST API endpoint integration tests
- **WebSocket**: Session manager lifecycle

Frontend type safety is verified via `npx tsc --noEmit`.

```bash
# Run backend tests
cd server && ./gradlew test

# TypeScript type check
cd client && npx tsc --noEmit
```

## Running Locally

```bash
# Terminal 1 — Backend
cd server && ./gradlew run

# Terminal 2 — Frontend
cd client && npm install && npm run dev
```

Open `http://localhost:5173` in a browser. The Vite dev server proxies API calls to the backend automatically.

## Tech Stack

- **Backend**: Kotlin 2.0 + Ktor 3.0 + Netty + Exposed ORM + SQLite + Gradle
- **Frontend**: React 18 + TypeScript 5.7 + Vite 6 + Zustand
- **Real-time**: WebSocket (Ktor native + browser native)
