import { useState } from 'react'
import type { Player, GameState, ResourceType, DevelopmentCardType } from '../../types/game'
import { RESOURCE_COLORS, RESOURCE_NAMES, BUILDING_COSTS, PLAYER_COLORS } from '../../types/game'
import { gameWebSocket } from '../../services/websocket'
import { verticesOfHex, sameVertex } from '../../utils/hexUtils'

const ALL_RESOURCES: ResourceType[] = ['BRICK', 'LUMBER', 'ORE', 'GRAIN', 'WOOL']

interface Props {
  player: Player
  gameState: GameState
  isMyTurn: boolean
}

export function PlayerPanel({ player, gameState, isMyTurn }: Props) {
  const phase = gameState.phase
  const turnPhase = gameState.turnPhase

  const [discardAmounts, setDiscardAmounts] = useState<Record<ResourceType, number>>({ BRICK: 0, LUMBER: 0, ORE: 0, GRAIN: 0, WOOL: 0 })
  const [yopResource1, setYopResource1] = useState<ResourceType>('BRICK')
  const [yopResource2, setYopResource2] = useState<ResourceType>('LUMBER')
  const [showYopPrompt, setShowYopPrompt] = useState(false)
  const [monopolyResource, setMonopolyResource] = useState<ResourceType>('BRICK')
  const [showMonopolyPrompt, setShowMonopolyPrompt] = useState(false)

  const handleRollDice = () => {
    gameWebSocket.send({ type: 'ROLL_DICE' })
  }

  const handleEndTurn = () => {
    gameWebSocket.send({ type: 'END_TURN' })
  }

  const handleBuyDevCard = () => {
    gameWebSocket.send({ type: 'BUY_DEVELOPMENT_CARD' })
  }

  const handlePlayDevCard = (cardType: DevelopmentCardType) => {
    switch (cardType) {
      case 'KNIGHT':
        gameWebSocket.send({ type: 'PLAY_KNIGHT' })
        break
      case 'ROAD_BUILDING':
        gameWebSocket.send({ type: 'PLAY_ROAD_BUILDING' })
        break
      case 'YEAR_OF_PLENTY':
        setShowYopPrompt(true)
        break
      case 'MONOPOLY':
        setShowMonopolyPrompt(true)
        break
      case 'VICTORY_POINT':
        // VP cards are automatic
        break
    }
  }

  const handleDiscardSubmit = () => {
    const resources: Partial<Record<ResourceType, number>> = {}
    ALL_RESOURCES.forEach(r => {
      if (discardAmounts[r] > 0) resources[r] = discardAmounts[r]
    })
    gameWebSocket.send({ type: 'DISCARD_RESOURCES', resources })
    setDiscardAmounts({ BRICK: 0, LUMBER: 0, ORE: 0, GRAIN: 0, WOOL: 0 })
  }

  const handleStealResource = (targetPlayerId: string) => {
    gameWebSocket.send({ type: 'STEAL_RESOURCE', targetPlayerId })
  }

  const handleYopSubmit = () => {
    gameWebSocket.send({ type: 'PLAY_YEAR_OF_PLENTY', resource1: yopResource1, resource2: yopResource2 })
    setShowYopPrompt(false)
  }

  const handleMonopolySubmit = () => {
    gameWebSocket.send({ type: 'PLAY_MONOPOLY', resource: monopolyResource })
    setShowMonopolyPrompt(false)
  }

  const canRollDice = isMyTurn && phase === 'MAIN' && turnPhase === 'ROLL_DICE'
  const canEndTurn = isMyTurn && phase === 'MAIN' && turnPhase === 'TRADE_BUILD'
  const canBuild = isMyTurn && phase === 'MAIN' && turnPhase === 'TRADE_BUILD'

  const myRoadCount = gameState.roads.filter(r => r.playerId === player.id).length
  const mySettlementCount = gameState.buildings.filter(b => b.playerId === player.id && b.type === 'SETTLEMENT').length
  const myCityCount = gameState.buildings.filter(b => b.playerId === player.id && b.type === 'CITY').length

  const hasResources = (cost: Partial<Record<ResourceType, number>>) => {
    return Object.entries(cost).every(
      ([res, amount]) => (player.resources[res as ResourceType] || 0) >= (amount || 0)
    )
  }

  const totalResources = Object.values(player.resources).reduce((a, b) => a + b, 0)
  const discardTotal = Object.values(discardAmounts).reduce((a, b) => a + b, 0)
  const discardTarget = Math.floor(totalResources / 2)
  const mustDiscard = gameState.discardingPlayerIds.includes(player.id)
  const showRobberSteal = isMyTurn && turnPhase === 'ROBBER_STEAL' && phase === 'MAIN'

  return (
    <div style={styles.panel}>
      <div style={styles.header}>
        <span style={{ ...styles.colorDot, background: PLAYER_COLORS[player.color] }} />
        <strong>{player.displayName}</strong>
        <span style={styles.vp}>{player.victoryPoints} VP</span>
      </div>

      {/* Resources */}
      <div style={styles.section}>
        <div style={styles.sectionTitle}>Resources ({totalResources})</div>
        <div style={styles.resources}>
          {(Object.keys(RESOURCE_NAMES) as ResourceType[]).map((res) => (
            <div key={res} style={styles.resourceItem}>
              <div style={{ ...styles.resourceBadge, background: RESOURCE_COLORS[res] }}>
                {player.resources[res] || 0}
              </div>
              <span style={styles.resourceLabel}>{RESOURCE_NAMES[res]}</span>
            </div>
          ))}
        </div>
      </div>

      {/* Pieces Left */}
      <div style={styles.section}>
        <div style={styles.sectionTitle}>Pieces Left</div>
        <div style={styles.supplyRow}>
          <span style={styles.supplyItem}>Roads: {15 - myRoadCount}</span>
          <span style={styles.supplyItem}>Settlements: {5 - mySettlementCount}</span>
          <span style={styles.supplyItem}>Cities: {4 - myCityCount}</span>
        </div>
      </div>

      {/* Road Building Mode */}
      {isMyTurn && gameState.roadBuildingRoadsLeft > 0 && (
        <div style={styles.section}>
          <div style={styles.roadBuildInfo}>Road Building: {gameState.roadBuildingRoadsLeft} road(s) left to place</div>
        </div>
      )}

      {/* Discard Resources UI */}
      {mustDiscard && turnPhase === 'DISCARD' && (
        <div style={styles.section}>
          <div style={{ ...styles.warning, marginBottom: '0.5rem' }}>
            You must discard {discardTarget} cards! (selected: {discardTotal})
          </div>
          <div style={styles.discardSelector}>
            {ALL_RESOURCES.map(res => (
              <div key={res} style={styles.discardRow}>
                <span style={{ ...styles.resDot, background: RESOURCE_COLORS[res] }} />
                <span style={styles.resName}>{RESOURCE_NAMES[res]}</span>
                <button
                  style={styles.resBtn}
                  onClick={() => setDiscardAmounts(prev => ({ ...prev, [res]: Math.max(0, prev[res] - 1) }))}
                >-</button>
                <span style={styles.resCount}>{discardAmounts[res]}</span>
                <button
                  style={styles.resBtn}
                  onClick={() => setDiscardAmounts(prev => ({
                    ...prev,
                    [res]: Math.min(player.resources[res] || 0, prev[res] + 1),
                  }))}
                >+</button>
                <span style={styles.resAvail}>/ {player.resources[res] || 0}</span>
              </div>
            ))}
          </div>
          <button
            onClick={handleDiscardSubmit}
            style={{ ...styles.actionBtn, opacity: discardTotal === discardTarget ? 1 : 0.5, marginTop: '0.5rem' }}
            disabled={discardTotal !== discardTarget}
          >
            Confirm Discard
          </button>
        </div>
      )}

      {/* Robber Steal UI */}
      {showRobberSteal && (
        <div style={styles.section}>
          <div style={styles.sectionTitle}>Choose a player to steal from</div>
          <div style={styles.actions}>
            {gameState.players
              .filter(p => {
                if (p.id === player.id) return false
                const totalRes = Object.values(p.resources).reduce((a, b) => a + b, 0)
                if (totalRes === 0) return false
                const robberVertices = verticesOfHex(gameState.robberLocation)
                return gameState.buildings.some(b =>
                  b.playerId === p.id && robberVertices.some(rv => sameVertex(rv, b.vertex))
                )
              })
              .map(p => (
                <button
                  key={p.id}
                  onClick={() => handleStealResource(p.id)}
                  style={{ ...styles.actionBtn, ...styles.stealBtn }}
                >
                  Steal from {p.displayName}
                </button>
              ))}
          </div>
        </div>
      )}

      {/* Year of Plenty Prompt */}
      {showYopPrompt && (
        <div style={styles.section}>
          <div style={styles.sectionTitle}>Year of Plenty - Pick 2 resources</div>
          <div style={styles.promptRow}>
            <label style={styles.promptLabel}>Resource 1:</label>
            <select value={yopResource1} onChange={e => setYopResource1(e.target.value as ResourceType)} style={styles.select}>
              {ALL_RESOURCES.map(r => <option key={r} value={r}>{RESOURCE_NAMES[r]}</option>)}
            </select>
          </div>
          <div style={styles.promptRow}>
            <label style={styles.promptLabel}>Resource 2:</label>
            <select value={yopResource2} onChange={e => setYopResource2(e.target.value as ResourceType)} style={styles.select}>
              {ALL_RESOURCES.map(r => <option key={r} value={r}>{RESOURCE_NAMES[r]}</option>)}
            </select>
          </div>
          <div style={styles.promptBtns}>
            <button onClick={handleYopSubmit} style={styles.actionBtn}>Confirm</button>
            <button onClick={() => setShowYopPrompt(false)} style={{ ...styles.actionBtn, ...styles.cancelBtn }}>Cancel</button>
          </div>
        </div>
      )}

      {/* Monopoly Prompt */}
      {showMonopolyPrompt && (
        <div style={styles.section}>
          <div style={styles.sectionTitle}>Monopoly - Pick a resource</div>
          <div style={styles.promptRow}>
            <label style={styles.promptLabel}>Resource:</label>
            <select value={monopolyResource} onChange={e => setMonopolyResource(e.target.value as ResourceType)} style={styles.select}>
              {ALL_RESOURCES.map(r => <option key={r} value={r}>{RESOURCE_NAMES[r]}</option>)}
            </select>
          </div>
          <div style={styles.promptBtns}>
            <button onClick={handleMonopolySubmit} style={styles.actionBtn}>Confirm</button>
            <button onClick={() => setShowMonopolyPrompt(false)} style={{ ...styles.actionBtn, ...styles.cancelBtn }}>Cancel</button>
          </div>
        </div>
      )}

      {/* Actions */}
      <div style={styles.section}>
        <div style={styles.sectionTitle}>Actions</div>
        <div style={styles.actions}>
          {canRollDice && (
            <button onClick={handleRollDice} style={styles.actionBtn}>
              Roll Dice
            </button>
          )}
          {canBuild && (
            <button
              onClick={handleBuyDevCard}
              style={{
                ...styles.actionBtn,
                ...styles.secondaryBtn,
                opacity: hasResources(BUILDING_COSTS.DEV_CARD) ? 1 : 0.5,
              }}
              disabled={!hasResources(BUILDING_COSTS.DEV_CARD)}
            >
              Buy Dev Card
            </button>
          )}
          {canEndTurn && (
            <button onClick={handleEndTurn} style={{ ...styles.actionBtn, ...styles.endTurnBtn }}>
              End Turn
            </button>
          )}
        </div>
      </div>

      {/* Dev Cards */}
      {player.devCards.length > 0 && (
        <div style={styles.section}>
          <div style={styles.sectionTitle}>Development Cards ({player.devCards.length})</div>
          <div style={styles.devCards}>
            {player.devCards.map((card, i) => (
              <button
                key={`${card}-${i}`}
                style={{
                  ...styles.devCardBtn,
                  opacity: canBuild && !player.hasPlayedDevCardThisTurn && card !== 'VICTORY_POINT' ? 1 : 0.5,
                }}
                onClick={() => handlePlayDevCard(card)}
                disabled={!canBuild || player.hasPlayedDevCardThisTurn || card === 'VICTORY_POINT'}
              >
                {DEV_CARD_NAMES[card]}
              </button>
            ))}
          </div>
        </div>
      )}

      {/* New Dev Cards (bought this turn) */}
      {player.newDevCards.length > 0 && (
        <div style={styles.section}>
          <div style={styles.sectionTitle}>New Cards (playable next turn)</div>
          <div style={styles.devCards}>
            {player.newDevCards.map((card, i) => (
              <button key={`new-${card}-${i}`} style={{ ...styles.devCardBtn, opacity: 0.5 }} disabled>
                {DEV_CARD_NAMES[card]}
              </button>
            ))}
          </div>
        </div>
      )}

      {/* Build Cost Reference */}
      <div style={styles.section}>
        <div style={styles.sectionTitle}>Build Costs</div>
        <div style={styles.costRef}>
          {Object.entries(BUILDING_COSTS).map(([name, cost]) => (
            <div key={name} style={styles.costRow}>
              <span style={styles.costName}>{name}</span>
              <span style={styles.costResources}>
                {Object.entries(cost).map(([res, amt]) => (
                  <span key={res} style={{ ...styles.costBadge, background: RESOURCE_COLORS[res as ResourceType] }}>
                    {amt}
                  </span>
                ))}
              </span>
            </div>
          ))}
        </div>
      </div>
    </div>
  )
}

const DEV_CARD_NAMES: Record<DevelopmentCardType, string> = {
  KNIGHT: 'Knight',
  VICTORY_POINT: 'VP',
  ROAD_BUILDING: 'Road Build',
  YEAR_OF_PLENTY: 'Year of Plenty',
  MONOPOLY: 'Monopoly',
}

const styles: Record<string, React.CSSProperties> = {
  panel: { background: '#fff', borderRadius: 8, padding: '0.75rem', marginBottom: '0.75rem', boxShadow: '0 1px 3px rgba(0,0,0,0.1)' },
  header: { display: 'flex', alignItems: 'center', gap: '0.5rem', marginBottom: '0.5rem' },
  colorDot: { width: 12, height: 12, borderRadius: '50%', display: 'inline-block' },
  vp: { marginLeft: 'auto', fontWeight: 'bold', color: '#e67e22' },
  section: { marginTop: '0.5rem' },
  sectionTitle: { fontSize: '0.8rem', fontWeight: 'bold', color: '#7f8c8d', marginBottom: '0.25rem' },
  resources: { display: 'flex', gap: '0.5rem', flexWrap: 'wrap' as const },
  resourceItem: { display: 'flex', flexDirection: 'column' as const, alignItems: 'center', gap: 2 },
  resourceBadge: { width: 32, height: 32, borderRadius: 6, display: 'flex', alignItems: 'center', justifyContent: 'center', color: '#fff', fontWeight: 'bold', fontSize: '0.9rem' },
  resourceLabel: { fontSize: '0.65rem', color: '#7f8c8d' },
  actions: { display: 'flex', gap: '0.5rem', flexWrap: 'wrap' as const },
  actionBtn: { padding: '0.4rem 0.75rem', borderRadius: 6, border: 'none', background: '#27ae60', color: '#fff', cursor: 'pointer', fontWeight: 'bold', fontSize: '0.85rem' },
  secondaryBtn: { background: '#8e44ad' },
  endTurnBtn: { background: '#e74c3c' },
  stealBtn: { background: '#d35400' },
  cancelBtn: { background: '#95a5a6' },
  warning: { color: '#e74c3c', fontWeight: 'bold', fontSize: '0.85rem' },
  devCards: { display: 'flex', gap: '0.25rem', flexWrap: 'wrap' as const },
  devCardBtn: { padding: '0.25rem 0.5rem', borderRadius: 4, border: '1px solid #bdc3c7', background: '#ffeaa7', cursor: 'pointer', fontSize: '0.75rem' },
  costRef: { fontSize: '0.75rem' },
  costRow: { display: 'flex', justifyContent: 'space-between', alignItems: 'center', padding: '2px 0' },
  costName: { color: '#555', fontSize: '0.7rem' },
  costResources: { display: 'flex', gap: 2 },
  costBadge: { width: 18, height: 18, borderRadius: 3, display: 'inline-flex', alignItems: 'center', justifyContent: 'center', color: '#fff', fontSize: '0.65rem', fontWeight: 'bold' },
  discardSelector: { display: 'flex', flexDirection: 'column' as const, gap: '0.25rem' },
  discardRow: { display: 'flex', alignItems: 'center', gap: '0.3rem', fontSize: '0.8rem' },
  resDot: { width: 10, height: 10, borderRadius: '50%', display: 'inline-block' },
  resName: { width: 50, fontSize: '0.75rem' },
  resBtn: { width: 22, height: 22, borderRadius: 4, border: '1px solid #bdc3c7', background: '#fff', cursor: 'pointer', fontSize: '0.8rem', display: 'flex', alignItems: 'center', justifyContent: 'center' },
  resCount: { width: 16, textAlign: 'center' as const, fontWeight: 'bold' },
  resAvail: { fontSize: '0.7rem', color: '#999' },
  promptRow: { display: 'flex', alignItems: 'center', gap: '0.5rem', marginBottom: '0.25rem' },
  promptLabel: { fontSize: '0.8rem', color: '#555', width: 80 },
  promptBtns: { display: 'flex', gap: '0.5rem', marginTop: '0.5rem' },
  select: { padding: '0.3rem', borderRadius: 4, border: '1px solid #bdc3c7', fontSize: '0.85rem', flex: 1 },
  supplyRow: { display: 'flex', gap: '0.75rem', flexWrap: 'wrap' as const, fontSize: '0.75rem' },
  supplyItem: { color: '#555' },
  roadBuildInfo: { color: '#e67e22', fontWeight: 'bold', fontSize: '0.85rem' },
}
