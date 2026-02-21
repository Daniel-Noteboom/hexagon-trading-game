import type { GameState, HexCoord, VertexCoord, EdgeCoord } from '../../types/game'
import { TILE_COLORS, PLAYER_COLORS } from '../../types/game'
import { hexToPixel, hexCorners, vertexToPixel, edgeLinePoints, BOARD_VIEW_BOX } from './hexLayout'
import { gameWebSocket } from '../../services/websocket'
import { verticesOfEdge, edgesOfVertex, adjacentVertices, sameVertex, sameEdge } from '../../utils/hexUtils'

interface Props {
  gameState: GameState
  myPlayerId: string
  isMyTurn: boolean
}

export function GameBoard({ gameState, myPlayerId, isMyTurn }: Props) {
  const handleVertexClick = (v: VertexCoord) => {
    const phase = gameState.phase
    if (phase === 'SETUP_FORWARD' || phase === 'SETUP_REVERSE') {
      if (!gameState.setupState.placedSettlement) {
        gameWebSocket.send({ type: 'PLACE_SETTLEMENT', vertex: v })
      }
    } else if (phase === 'MAIN' && gameState.turnPhase === 'TRADE_BUILD' && isMyTurn) {
      // Check if this vertex has our settlement (upgrade to city)
      const building = gameState.buildings.find(
        b => b.vertex.q === v.q && b.vertex.r === v.r && b.vertex.dir === v.dir
      )
      if (building && building.playerId === myPlayerId && building.type === 'SETTLEMENT') {
        gameWebSocket.send({ type: 'PLACE_CITY', vertex: v })
      } else if (!building) {
        gameWebSocket.send({ type: 'PLACE_SETTLEMENT', vertex: v })
      }
    }
  }

  const handleEdgeClick = (e: EdgeCoord) => {
    gameWebSocket.send({ type: 'PLACE_ROAD', edge: e })
  }

  const handleHexClick = (hex: HexCoord) => {
    if (gameState.turnPhase === 'ROBBER_MOVE' && isMyTurn) {
      gameWebSocket.send({ type: 'MOVE_ROBBER', hex })
    }
  }

  return (
    <svg
      viewBox={`${BOARD_VIEW_BOX.x} ${BOARD_VIEW_BOX.y} ${BOARD_VIEW_BOX.width} ${BOARD_VIEW_BOX.height}`}
      style={{ width: '100%', height: '100%', maxWidth: 600, maxHeight: 600 }}
    >
      {/* Hex tiles */}
      {gameState.tiles.map((tile) => {
        const { x, y } = hexToPixel(tile.coord)
        const corners = hexCorners(x, y)
        const points = corners.map(c => `${c.x},${c.y}`).join(' ')
        const color = TILE_COLORS[tile.tileType]

        return (
          <g key={`hex-${tile.coord.q}-${tile.coord.r}`} onClick={() => handleHexClick(tile.coord)} style={{ cursor: gameState.turnPhase === 'ROBBER_MOVE' ? 'pointer' : 'default' }}>
            <polygon
              points={points}
              fill={color}
              stroke="#8B7355"
              strokeWidth={2}
              opacity={0.9}
            />
            {tile.diceNumber && (
              <>
                <circle cx={x} cy={y} r={14} fill="#fff" stroke="#333" strokeWidth={1} />
                <text
                  x={x} y={y + 5}
                  textAnchor="middle"
                  fontSize={12}
                  fontWeight="bold"
                  fill={tile.diceNumber === 6 || tile.diceNumber === 8 ? '#e74c3c' : '#333'}
                >
                  {tile.diceNumber}
                </text>
              </>
            )}
            {tile.hasRobber && (
              <text x={x} y={y + (tile.diceNumber ? 25 : 5)} textAnchor="middle" fontSize={16}>
                🏴
              </text>
            )}
          </g>
        )
      })}

      {/* Roads */}
      {gameState.roads.map((road, i) => {
        const { x1, y1, x2, y2 } = edgeLinePoints(road.edge)
        const player = gameState.players.find(p => p.id === road.playerId)
        const color = player ? PLAYER_COLORS[player.color] : '#999'
        return (
          <line
            key={`road-${i}`}
            x1={x1} y1={y1} x2={x2} y2={y2}
            stroke={color}
            strokeWidth={5}
            strokeLinecap="round"
          />
        )
      })}

      {/* Edge click targets (invisible wider lines) */}
      {isMyTurn && (gameState.phase === 'SETUP_FORWARD' || gameState.phase === 'SETUP_REVERSE' || gameState.turnPhase === 'TRADE_BUILD' || gameState.roadBuildingRoadsLeft > 0) && (
        <EdgeClickTargets gameState={gameState} myPlayerId={myPlayerId} onClick={handleEdgeClick} />
      )}

      {/* Buildings */}
      {gameState.buildings.map((building, i) => {
        const { x, y } = vertexToPixel(building.vertex)
        const player = gameState.players.find(p => p.id === building.playerId)
        const color = player ? PLAYER_COLORS[player.color] : '#999'

        if (building.type === 'SETTLEMENT') {
          return (
            <g key={`building-${i}`}>
              <circle cx={x} cy={y} r={7} fill={color} stroke="#333" strokeWidth={1.5} />
            </g>
          )
        } else {
          return (
            <g key={`building-${i}`}>
              <rect x={x - 8} y={y - 8} width={16} height={16} fill={color} stroke="#333" strokeWidth={1.5} rx={2} />
            </g>
          )
        }
      })}

      {/* Vertex click targets */}
      {isMyTurn && (gameState.phase === 'SETUP_FORWARD' || gameState.phase === 'SETUP_REVERSE' || gameState.turnPhase === 'TRADE_BUILD') && (
        <VertexClickTargets gameState={gameState} myPlayerId={myPlayerId} onClick={handleVertexClick} />
      )}

      {/* Port labels */}
      {gameState.ports.map((port, i) => {
        const p1 = vertexToPixel(port.vertices[0])
        const p2 = vertexToPixel(port.vertices[1])
        const mx = (p1.x + p2.x) / 2
        const my = (p1.y + p2.y) / 2
        // Push the label outward from center
        const dist = Math.sqrt(mx * mx + my * my)
        const scale = dist > 0 ? (dist + 20) / dist : 1
        const lx = mx * scale
        const ly = my * scale
        const label = port.portType === 'GENERIC_3_1' ? '3:1' : port.portType.replace('_2_1', '').split('_').pop()?.slice(0, 2) + ' 2:1'

        return (
          <text key={`port-${i}`} x={lx} y={ly} textAnchor="middle" fontSize={8} fill="#fff" fontWeight="bold">
            {label}
          </text>
        )
      })}
    </svg>
  )
}

function VertexClickTargets({ gameState, myPlayerId, onClick }: { gameState: GameState; myPlayerId: string; onClick: (v: VertexCoord) => void }) {
  const vertices = new Set<string>()
  const vertexList: VertexCoord[] = []
  const isSetup = gameState.phase === 'SETUP_FORWARD' || gameState.phase === 'SETUP_REVERSE'

  for (const tile of gameState.tiles) {
    const { q, r } = tile.coord
    const verts: VertexCoord[] = [
      { q, r, dir: 'N' }, { q: q - 1, r, dir: 'S' }, { q: q + 1, r: r - 1, dir: 'N' },
      { q, r, dir: 'S' }, { q: q + 1, r, dir: 'N' }, { q: q - 1, r: r + 1, dir: 'S' },
    ]
    for (const v of verts) {
      const key = `${v.q},${v.r},${v.dir}`
      if (!vertices.has(key)) {
        vertices.add(key)
        vertexList.push(v)
      }
    }
  }

  const isValidVertex = (v: VertexCoord): boolean => {
    // Check if vertex is occupied
    const existingBuilding = gameState.buildings.find(b => sameVertex(b.vertex, v))
    if (existingBuilding) {
      // Allow clicking own settlements (for city upgrade) during main phase
      return !isSetup && existingBuilding.playerId === myPlayerId && existingBuilding.type === 'SETTLEMENT'
    }
    // Distance rule: no adjacent buildings
    if (adjacentVertices(v).some(adj => gameState.buildings.some(b => sameVertex(b.vertex, adj)))) return false
    // During setup, skip road connectivity check
    if (isSetup) return true
    // Must connect to own road
    return edgesOfVertex(v).some(edge =>
      gameState.roads.some(r => r.playerId === myPlayerId && sameEdge(r.edge, edge))
    )
  }

  return (
    <>
      {vertexList.map((v) => {
        if (!isValidVertex(v)) return null

        const { x, y } = vertexToPixel(v)
        return (
          <circle
            key={`vt-${v.q}-${v.r}-${v.dir}`}
            cx={x} cy={y} r={8}
            fill="transparent"
            stroke="transparent"
            style={{ cursor: 'pointer' }}
            onClick={() => onClick(v)}
            onMouseEnter={(e) => { (e.target as SVGCircleElement).setAttribute('fill', 'rgba(255,255,255,0.4)') }}
            onMouseLeave={(e) => { (e.target as SVGCircleElement).setAttribute('fill', 'transparent') }}
          />
        )
      })}
    </>
  )
}

function EdgeClickTargets({ gameState, myPlayerId, onClick }: { gameState: GameState; myPlayerId: string; onClick: (e: EdgeCoord) => void }) {
  const edges = new Set<string>()
  const edgeList: EdgeCoord[] = []
  const isSetup = gameState.phase === 'SETUP_FORWARD' || gameState.phase === 'SETUP_REVERSE'

  for (const tile of gameState.tiles) {
    const { q, r } = tile.coord
    const es: EdgeCoord[] = [
      { q, r, dir: 'NE' }, { q, r, dir: 'E' }, { q, r, dir: 'SE' },
      { q: q - 1, r: r + 1, dir: 'NE' }, { q: q - 1, r, dir: 'E' }, { q, r: r - 1, dir: 'SE' },
    ]
    for (const e of es) {
      const key = `${e.q},${e.r},${e.dir}`
      if (!edges.has(key)) {
        edges.add(key)
        edgeList.push(e)
      }
    }
  }

  const isValidEdge = (edge: EdgeCoord): boolean => {
    // Already occupied
    if (gameState.roads.some(r => sameEdge(r.edge, edge))) return false

    if (isSetup) {
      // During setup, only show edges adjacent to the just-placed settlement
      const lastVertex = gameState.setupState.lastSettlementVertex
      if (!lastVertex) return false
      const edgeVerts = verticesOfEdge(edge)
      return edgeVerts.some(v => sameVertex(v, lastVertex))
    }

    // During MAIN/road building: only show edges connected to player's existing network
    const edgeVerts = verticesOfEdge(edge)
    return edgeVerts.some(v => {
      // Has own building at vertex
      if (gameState.buildings.some(b => b.playerId === myPlayerId && sameVertex(b.vertex, v))) return true
      // Opponent building blocks road connectivity through this vertex
      if (gameState.buildings.some(b => b.playerId !== myPlayerId && sameVertex(b.vertex, v))) return false
      // Has own road sharing this vertex
      return edgesOfVertex(v).some(adjEdge =>
        !sameEdge(adjEdge, edge) && gameState.roads.some(r => r.playerId === myPlayerId && sameEdge(r.edge, adjEdge))
      )
    })
  }

  return (
    <>
      {edgeList.map((e) => {
        if (!isValidEdge(e)) return null

        const { x1, y1, x2, y2 } = edgeLinePoints(e)
        return (
          <line
            key={`et-${e.q}-${e.r}-${e.dir}`}
            x1={x1} y1={y1} x2={x2} y2={y2}
            stroke="transparent"
            strokeWidth={10}
            style={{ cursor: 'pointer' }}
            onClick={() => onClick(e)}
            onMouseEnter={(ev) => { (ev.target as SVGLineElement).setAttribute('stroke', 'rgba(255,255,255,0.3)') }}
            onMouseLeave={(ev) => { (ev.target as SVGLineElement).setAttribute('stroke', 'transparent') }}
          />
        )
      })}
    </>
  )
}
