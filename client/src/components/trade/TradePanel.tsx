import { useState } from 'react'
import type { GameState, ResourceType } from '../../types/game'
import { RESOURCE_NAMES, RESOURCE_COLORS } from '../../types/game'
import { gameWebSocket } from '../../services/websocket'
import { useGameStore } from '../../stores/gameStore'

interface Props {
  gameState: GameState
  myPlayerId: string
  isMyTurn: boolean
}

const ALL_RESOURCES: ResourceType[] = ['BRICK', 'LUMBER', 'ORE', 'GRAIN', 'WOOL']

export function TradePanel({ gameState, myPlayerId, isMyTurn }: Props) {
  const [tradeMode, setTradeMode] = useState<'none' | 'player' | 'bank'>('none')
  const [offering, setOffering] = useState<Record<ResourceType, number>>({ BRICK: 0, LUMBER: 0, ORE: 0, GRAIN: 0, WOOL: 0 })
  const [requesting, setRequesting] = useState<Record<ResourceType, number>>({ BRICK: 0, LUMBER: 0, ORE: 0, GRAIN: 0, WOOL: 0 })
  const [bankGiving, setBankGiving] = useState<ResourceType>('BRICK')
  const [bankReceiving, setBankReceiving] = useState<ResourceType>('ORE')

  const tradeResult = useGameStore(s => s.tradeResult)
  const canTrade = isMyTurn && gameState.phase === 'MAIN' && gameState.turnPhase === 'TRADE_BUILD'
  const pendingTrade = gameState.pendingTrade

  const handleOfferTrade = () => {
    const off: Partial<Record<ResourceType, number>> = {}
    const req: Partial<Record<ResourceType, number>> = {}
    ALL_RESOURCES.forEach(r => {
      if (offering[r] > 0) off[r] = offering[r]
      if (requesting[r] > 0) req[r] = requesting[r]
    })
    if (Object.keys(off).length === 0 || Object.keys(req).length === 0) return
    gameWebSocket.send({ type: 'OFFER_TRADE', offering: off, requesting: req })
    setTradeMode('none')
    resetAmounts()
  }

  const myPlayer = gameState.players.find(p => p.id === myPlayerId)

  const getBankRatio = (resource: ResourceType): number => {
    let ratio = 4
    for (const port of gameState.ports) {
      const hasPort = gameState.buildings.some(b =>
        b.playerId === myPlayerId &&
        port.vertices.some(v => v.q === b.vertex.q && v.r === b.vertex.r && v.dir === b.vertex.dir)
      )
      if (hasPort) {
        if (port.portType === 'GENERIC_3_1' && ratio > 3) ratio = 3
        const specific = port.portType.replace('_2_1', '') as ResourceType
        if (specific === resource && ratio > 2) ratio = 2
      }
    }
    return ratio
  }

  const bankRatio = getBankRatio(bankGiving)
  const bankGivingCount = myPlayer?.resources[bankGiving] || 0
  const canAffordBankTrade = bankGivingCount >= bankRatio

  const handleBankTrade = () => {
    if (!myPlayer || !canAffordBankTrade) return
    gameWebSocket.send({ type: 'BANK_TRADE', giving: bankGiving, givingAmount: bankRatio, receiving: bankReceiving })
  }

  const handleAcceptTrade = () => {
    gameWebSocket.send({ type: 'ACCEPT_TRADE' })
  }

  const handleDeclineTrade = () => {
    gameWebSocket.send({ type: 'DECLINE_TRADE' })
  }

  const resetAmounts = () => {
    setOffering({ BRICK: 0, LUMBER: 0, ORE: 0, GRAIN: 0, WOOL: 0 })
    setRequesting({ BRICK: 0, LUMBER: 0, ORE: 0, GRAIN: 0, WOOL: 0 })
  }

  // Show trade result notification
  if (tradeResult && !pendingTrade) {
    const isAccepted = tradeResult.type === 'accepted'
    return (
      <div style={styles.panel}>
        <div style={{
          ...styles.title,
          color: isAccepted ? '#27ae60' : '#e74c3c',
        }}>
          Trade {isAccepted ? 'accepted' : 'declined'} by {tradeResult.playerName}
        </div>
      </div>
    )
  }

  // Show incoming trade offer
  if (pendingTrade && pendingTrade.fromPlayerId !== myPlayerId) {
    const fromPlayer = gameState.players.find(p => p.id === pendingTrade.fromPlayerId)
    return (
      <div style={styles.panel}>
        <div style={styles.title}>Trade Offer from {fromPlayer?.displayName}</div>
        <div style={styles.tradeRow}>
          <div>
            <div style={styles.label}>Offering you:</div>
            {Object.entries(pendingTrade.offering).map(([res, amt]) => (
              <span key={res} style={styles.tradeBadge}>
                {amt} {RESOURCE_NAMES[res as ResourceType]}
              </span>
            ))}
          </div>
          <div>
            <div style={styles.label}>Wants:</div>
            {Object.entries(pendingTrade.requesting).map(([res, amt]) => (
              <span key={res} style={styles.tradeBadge}>
                {amt} {RESOURCE_NAMES[res as ResourceType]}
              </span>
            ))}
          </div>
        </div>
        <div style={styles.btnRow}>
          <button onClick={handleAcceptTrade} style={styles.acceptBtn}>Accept</button>
          <button onClick={handleDeclineTrade} style={styles.declineBtn}>Decline</button>
        </div>
      </div>
    )
  }

  if (!canTrade) return null

  return (
    <div style={styles.panel}>
      <div style={styles.title}>Trade</div>

      {tradeMode === 'none' && (
        <div style={styles.btnRow}>
          <button onClick={() => setTradeMode('player')} style={styles.tradeBtn}>Player Trade</button>
          <button onClick={() => setTradeMode('bank')} style={styles.tradeBtn}>Bank Trade</button>
        </div>
      )}

      {tradeMode === 'player' && (
        <div>
          <div style={styles.label}>You give:</div>
          <ResourceSelector values={offering} onChange={setOffering} />
          <div style={styles.label}>You want:</div>
          <ResourceSelector values={requesting} onChange={setRequesting} />
          <div style={styles.btnRow}>
            <button onClick={handleOfferTrade} style={styles.acceptBtn}>Send Offer</button>
            <button onClick={() => { setTradeMode('none'); resetAmounts() }} style={styles.cancelBtn}>Cancel</button>
          </div>
        </div>
      )}

      {tradeMode === 'bank' && (
        <div>
          <div style={styles.label}>Give {bankRatio} {RESOURCE_NAMES[bankGiving]} (you have: {bankGivingCount}):</div>
          <select value={bankGiving} onChange={e => setBankGiving(e.target.value as ResourceType)} style={styles.select}>
            {ALL_RESOURCES.map(r => {
              const ratio = getBankRatio(r)
              return <option key={r} value={r}>{RESOURCE_NAMES[r]} ({ratio}:1)</option>
            })}
          </select>
          <div style={styles.label}>Receive 1:</div>
          <select value={bankReceiving} onChange={e => setBankReceiving(e.target.value as ResourceType)} style={styles.select}>
            {ALL_RESOURCES.filter(r => r !== bankGiving).map(r => <option key={r} value={r}>{RESOURCE_NAMES[r]}</option>)}
          </select>
          <div style={styles.btnRow}>
            <button
              onClick={handleBankTrade}
              style={{ ...styles.acceptBtn, opacity: canAffordBankTrade ? 1 : 0.5 }}
              disabled={!canAffordBankTrade}
            >
              Trade {bankRatio}:1
            </button>
            <button onClick={() => setTradeMode('none')} style={styles.cancelBtn}>Cancel</button>
          </div>
        </div>
      )}
    </div>
  )
}

function ResourceSelector({ values, onChange }: {
  values: Record<ResourceType, number>
  onChange: (v: Record<ResourceType, number>) => void
}) {
  return (
    <div style={styles.resSelector}>
      {ALL_RESOURCES.map(res => (
        <div key={res} style={styles.resSelectorItem}>
          <span style={{ ...styles.resDot, background: RESOURCE_COLORS[res] }} />
          <span style={styles.resName}>{RESOURCE_NAMES[res]}</span>
          <button
            style={styles.resBtn}
            onClick={() => onChange({ ...values, [res]: Math.max(0, values[res] - 1) })}
          >-</button>
          <span style={styles.resCount}>{values[res]}</span>
          <button
            style={styles.resBtn}
            onClick={() => onChange({ ...values, [res]: values[res] + 1 })}
          >+</button>
        </div>
      ))}
    </div>
  )
}

const styles: Record<string, React.CSSProperties> = {
  panel: { background: '#fff', borderRadius: 8, padding: '0.75rem', marginBottom: '0.75rem', boxShadow: '0 1px 3px rgba(0,0,0,0.1)' },
  title: { fontWeight: 'bold', marginBottom: '0.5rem', fontSize: '0.9rem' },
  label: { fontSize: '0.8rem', color: '#7f8c8d', margin: '0.25rem 0' },
  btnRow: { display: 'flex', gap: '0.5rem', marginTop: '0.5rem' },
  tradeBtn: { padding: '0.35rem 0.7rem', borderRadius: 6, border: 'none', background: '#2980b9', color: '#fff', cursor: 'pointer', fontSize: '0.8rem' },
  acceptBtn: { padding: '0.35rem 0.7rem', borderRadius: 6, border: 'none', background: '#27ae60', color: '#fff', cursor: 'pointer', fontSize: '0.8rem' },
  declineBtn: { padding: '0.35rem 0.7rem', borderRadius: 6, border: 'none', background: '#e74c3c', color: '#fff', cursor: 'pointer', fontSize: '0.8rem' },
  cancelBtn: { padding: '0.35rem 0.7rem', borderRadius: 6, border: 'none', background: '#95a5a6', color: '#fff', cursor: 'pointer', fontSize: '0.8rem' },
  tradeRow: { display: 'flex', gap: '1rem', marginBottom: '0.5rem' },
  tradeBadge: { display: 'inline-block', background: '#ecf0f1', padding: '0.15rem 0.4rem', borderRadius: 4, fontSize: '0.75rem', marginRight: '0.25rem' },
  select: { padding: '0.3rem', borderRadius: 4, border: '1px solid #bdc3c7', fontSize: '0.85rem', width: '100%', marginBottom: '0.25rem' },
  resSelector: { display: 'flex', flexDirection: 'column' as const, gap: '0.25rem', margin: '0.25rem 0' },
  resSelectorItem: { display: 'flex', alignItems: 'center', gap: '0.3rem', fontSize: '0.8rem' },
  resDot: { width: 10, height: 10, borderRadius: '50%', display: 'inline-block' },
  resName: { width: 50, fontSize: '0.75rem' },
  resBtn: { width: 22, height: 22, borderRadius: 4, border: '1px solid #bdc3c7', background: '#fff', cursor: 'pointer', fontSize: '0.8rem', display: 'flex', alignItems: 'center', justifyContent: 'center' },
  resCount: { width: 16, textAlign: 'center' as const, fontWeight: 'bold' },
}
