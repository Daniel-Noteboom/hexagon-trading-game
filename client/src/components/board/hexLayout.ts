import type { HexCoord, VertexCoord, EdgeCoord } from '../../types/game'

const HEX_SIZE = 50

/** Flat-top hex: pixel position of hex center from axial coords. */
export function hexToPixel(hex: HexCoord): { x: number; y: number } {
  const x = HEX_SIZE * (3 / 2) * hex.q
  const y = HEX_SIZE * (Math.sqrt(3) / 2 * hex.q + Math.sqrt(3) * hex.r)
  return { x, y }
}

/** Returns the 6 pixel corners of a flat-top hex at center (cx, cy). */
export function hexCorners(cx: number, cy: number): { x: number; y: number }[] {
  const corners: { x: number; y: number }[] = []
  for (let i = 0; i < 6; i++) {
    const angleDeg = 60 * i
    const angleRad = (Math.PI / 180) * angleDeg
    corners.push({
      x: cx + HEX_SIZE * Math.cos(angleRad),
      y: cy + HEX_SIZE * Math.sin(angleRad),
    })
  }
  return corners
}

/** Pixel position for a vertex. */
export function vertexToPixel(v: VertexCoord): { x: number; y: number } {
  // N vertex = left vertex of hex (q,r) → hex center + (-HEX_SIZE, 0)
  // S vertex = right vertex of hex (q,r) → hex center + (+HEX_SIZE, 0)
  const center = hexToPixel({ q: v.q, r: v.r })
  if (v.dir === 'N') {
    return { x: center.x - HEX_SIZE, y: center.y }
  } else {
    return { x: center.x + HEX_SIZE, y: center.y }
  }
}

/** Pixel position for an edge (midpoint of its two vertices). */
export function edgeToPixel(e: EdgeCoord): { x: number; y: number } {
  const vertices = edgeVertices(e)
  const p1 = vertexToPixel(vertices[0])
  const p2 = vertexToPixel(vertices[1])
  return { x: (p1.x + p2.x) / 2, y: (p1.y + p2.y) / 2 }
}

/** Returns the two vertex coords of an edge. */
function edgeVertices(e: EdgeCoord): [VertexCoord, VertexCoord] {
  const { q, r, dir } = e
  switch (dir) {
    case 'NE': return [{ q: q + 1, r: r - 1, dir: 'N' }, { q, r, dir: 'S' }]
    case 'E': return [{ q, r, dir: 'S' }, { q: q + 1, r, dir: 'N' }]
    case 'SE': return [{ q: q + 1, r, dir: 'N' }, { q: q - 1, r: r + 1, dir: 'S' }]
  }
}

/** Returns pixel endpoints for drawing an edge as a line. */
export function edgeLinePoints(e: EdgeCoord): { x1: number; y1: number; x2: number; y2: number } {
  const [v1, v2] = edgeVertices(e)
  const p1 = vertexToPixel(v1)
  const p2 = vertexToPixel(v2)
  return { x1: p1.x, y1: p1.y, x2: p2.x, y2: p2.y }
}

/** SVG viewBox dimensions for the board. */
export const BOARD_VIEW_BOX = {
  x: -250,
  y: -250,
  width: 500,
  height: 500,
}

export { HEX_SIZE }
