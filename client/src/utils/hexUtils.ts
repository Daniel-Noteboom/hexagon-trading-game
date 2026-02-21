import type { VertexCoord, EdgeCoord, HexCoord } from '../types/game'

export function sameVertex(a: VertexCoord, b: VertexCoord): boolean {
  return a.q === b.q && a.r === b.r && a.dir === b.dir
}

export function sameEdge(a: EdgeCoord, b: EdgeCoord): boolean {
  return a.q === b.q && a.r === b.r && a.dir === b.dir
}

/**
 * Returns the 2 vertices at either end of an edge.
 * Ported from backend HexUtils.kt verticesOfEdge().
 */
export function verticesOfEdge(edge: EdgeCoord): [VertexCoord, VertexCoord] {
  const { q, r, dir } = edge
  switch (dir) {
    case 'NE': return [{ q: q + 1, r: r - 1, dir: 'N' }, { q, r, dir: 'S' }]
    case 'E': return [{ q, r, dir: 'S' }, { q: q + 1, r, dir: 'N' }]
    case 'SE': return [{ q: q + 1, r, dir: 'N' }, { q: q - 1, r: r + 1, dir: 'S' }]
  }
}

/**
 * Returns the 2-3 edges connected to a vertex.
 * Ported from backend HexUtils.kt edgesOfVertex().
 */
export function edgesOfVertex(vertex: VertexCoord): EdgeCoord[] {
  const { q, r, dir } = vertex
  if (dir === 'N') {
    return [
      { q: q - 1, r, dir: 'E' },
      { q: q - 1, r: r + 1, dir: 'NE' },
      { q: q - 1, r, dir: 'SE' },
    ]
  } else {
    return [
      { q, r, dir: 'NE' },
      { q, r, dir: 'E' },
      { q: q + 1, r: r - 1, dir: 'SE' },
    ]
  }
}

/**
 * Returns the 2-3 vertices adjacent to a vertex (connected by an edge).
 * Ported from backend HexUtils.kt adjacentVertices().
 */
/**
 * Returns the 6 vertices of a hex tile.
 * Ported from backend HexUtils.kt verticesOfHex().
 */
export function verticesOfHex(hex: HexCoord): VertexCoord[] {
  const { q, r } = hex
  return [
    { q, r, dir: 'N' },
    { q: q - 1, r, dir: 'S' },
    { q: q + 1, r: r - 1, dir: 'N' },
    { q, r, dir: 'S' },
    { q: q + 1, r, dir: 'N' },
    { q: q - 1, r: r + 1, dir: 'S' },
  ]
}

export function adjacentVertices(vertex: VertexCoord): VertexCoord[] {
  const { q, r, dir } = vertex
  if (dir === 'N') {
    return [
      { q: q - 1, r, dir: 'S' },
      { q: q - 1, r: r + 1, dir: 'S' },
      { q: q - 2, r: r + 1, dir: 'S' },
    ]
  } else {
    return [
      { q: q + 1, r: r - 1, dir: 'N' },
      { q: q + 1, r, dir: 'N' },
      { q: q + 2, r: r - 1, dir: 'N' },
    ]
  }
}
