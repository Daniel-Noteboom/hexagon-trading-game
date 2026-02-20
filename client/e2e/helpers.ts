import { type Page, expect } from '@playwright/test'

/**
 * Clear localStorage to ensure a fresh session.
 */
export async function clearSession(page: Page) {
  await page.evaluate(() => localStorage.removeItem('catanPlayer'))
}

/**
 * Register a player on the landing page and wait for lobby redirect.
 */
export async function registerPlayer(page: Page, name: string) {
  await page.goto('/')
  await clearSession(page)
  await page.reload()

  await page.getByPlaceholder('Your display name').fill(name)
  await page.getByRole('button', { name: 'Enter Game' }).click()
  await page.waitForURL('/lobby')
  await expect(page.getByText(`Welcome, ${name}`)).toBeVisible()
}

/**
 * Create a new game from the lobby and return the gameId.
 */
export async function createAndJoinGame(page: Page): Promise<string> {
  await page.getByRole('button', { name: 'Create New Game' }).click()
  await page.waitForURL(/\/game\/.+/)
  const url = page.url()
  const gameId = url.split('/game/')[1]
  return gameId
}

/**
 * Wait for the game board to render by checking for game UI elements.
 */
export async function waitForGameBoard(page: Page) {
  await expect(page.getByText('Build Costs')).toBeVisible({ timeout: 15000 })
}

/**
 * Send a game action via the exposed __sendGameAction helper (dev mode only).
 */
export async function sendAction(page: Page, action: Record<string, unknown>) {
  await page.evaluate((a) => {
    if (!(window as any).__sendGameAction) throw new Error('__sendGameAction not available')
    ;(window as any).__sendGameAction(a)
  }, action)
}

/**
 * Wait for the status bar to show a particular phase / turnPhase.
 * The status bar renders: "{phase} / {turnPhase}"
 */
export async function waitForPhase(page: Page, phase: string, turnPhase?: string, timeout = 15000) {
  const text = turnPhase ? `${phase} / ${turnPhase}` : phase
  await expect(page.getByText(text, { exact: false })).toBeVisible({ timeout })
}

/**
 * Wait until it's the given page's player's turn (Roll Dice or End Turn visible).
 */
export async function waitForMyTurn(page: Page, timeout = 30000) {
  await expect(
    page.getByRole('button', { name: /Roll Dice|End Turn/ })
  ).toBeVisible({ timeout })
}

/**
 * Returns the page that currently has the "Roll Dice" button visible,
 * i.e. the active player's page.
 */
export async function getActivePlayerPage(page1: Page, page2: Page, timeout = 15000): Promise<Page> {
  // Race: whichever page shows "Roll Dice" first
  const result = await Promise.race([
    page1.getByRole('button', { name: 'Roll Dice' }).waitFor({ timeout }).then(() => page1),
    page2.getByRole('button', { name: 'Roll Dice' }).waitFor({ timeout }).then(() => page2),
  ])
  return result
}

/**
 * Complete the entire setup phase (SETUP_FORWARD + SETUP_REVERSE) for 2 players.
 * Each player places 2 settlements and 2 roads by clicking vertex/edge targets.
 */
export async function completeSetupPhase(page1: Page, page2: Page) {
  // Setup requires 4 placement rounds:
  // SETUP_FORWARD: player1 (settlement+road), player2 (settlement+road)
  // SETUP_REVERSE: player2 (settlement+road), player1 (settlement+road)
  for (let round = 0; round < 4; round++) {
    // Determine which page is active
    const activePage = await getSetupActivePage(page1, page2)

    // Click a vertex target to place settlement
    await clickSvgTarget(activePage, 'circle', 'settlement')

    // Small delay for state to update
    await activePage.waitForTimeout(500)

    // Click an edge target to place road
    await clickSvgTarget(activePage, 'line', 'road')

    // Wait for the placement to process
    await activePage.waitForTimeout(1000)
  }

  // Verify we've reached MAIN phase
  await waitForPhase(page1, 'MAIN', undefined, 20000)
}

/**
 * Determine which page is the active setup player.
 * During setup, the active player has clickable vertex targets (transparent circles with pointer cursor).
 */
async function getSetupActivePage(page1: Page, page2: Page, timeout = 15000): Promise<Page> {
  // During setup, look for vertex click targets (transparent circles that act as click targets)
  // The active player will have circles with cursor:pointer style
  const result = await Promise.race([
    page1.locator('svg circle[style*="cursor: pointer"], svg circle[style*="cursor:pointer"]').first().waitFor({ timeout }).then(() => page1),
    page2.locator('svg circle[style*="cursor: pointer"], svg circle[style*="cursor:pointer"]').first().waitFor({ timeout }).then(() => page2),
  ])
  return result
}

/**
 * Click a visible SVG target element (vertex circle or edge line) for placement.
 */
async function clickSvgTarget(page: Page, element: 'circle' | 'line', _type: string) {
  if (element === 'circle') {
    // Find vertex click targets (transparent, cursor:pointer circles)
    const targets = page.locator('svg circle[style*="cursor: pointer"], svg circle[style*="cursor:pointer"]')
    const count = await targets.count()
    if (count === 0) throw new Error(`No ${_type} click targets found`)
    // Click a target near the middle of the list for more reliable placement
    const idx = Math.floor(count / 2)
    await targets.nth(idx).click({ force: true })
  } else {
    // Find edge click targets (transparent, cursor:pointer lines with wide stroke)
    const targets = page.locator('svg line[style*="cursor: pointer"], svg line[style*="cursor:pointer"]')
    const count = await targets.count()
    if (count === 0) throw new Error(`No ${_type} click targets found`)
    // Click first available edge target
    const idx = Math.floor(count / 2)
    await targets.nth(idx).click({ force: true })
  }
}

/**
 * Click "Roll Dice" button and wait for the phase to change.
 */
export async function rollDice(page: Page) {
  await page.getByRole('button', { name: 'Roll Dice' }).click()
  // Wait for dice roll to process — turn phase should change from ROLL_DICE
  // It could go to TRADE_BUILD, DISCARD, or ROBBER_MOVE
  await expect(page.getByRole('button', { name: 'Roll Dice' })).not.toBeVisible({ timeout: 10000 })
}

/**
 * Click "End Turn" button and wait for it to disappear.
 */
export async function endTurn(page: Page) {
  await page.getByRole('button', { name: 'End Turn' }).click()
  await expect(page.getByRole('button', { name: 'End Turn' })).not.toBeVisible({ timeout: 10000 })
}

/**
 * Set up a full two-player game: register both players, create game, join, start,
 * and complete the setup phase. Returns { page1, page2, gameId, context1, context2 }.
 */
export async function setupTwoPlayerGame(browser: any, baseURL: string) {
  const context1 = await browser.newContext({ baseURL })
  const context2 = await browser.newContext({ baseURL })
  const page1 = await context1.newPage()
  const page2 = await context2.newPage()

  const ts = Date.now()
  await registerPlayer(page1, `P1_${ts}`)
  const gameId = await createAndJoinGame(page1)
  await expect(page1.getByText('Waiting Room')).toBeVisible({ timeout: 10000 })

  await registerPlayer(page2, `P2_${ts}`)
  await page2.evaluate(async (gId) => {
    const stored = localStorage.getItem('catanPlayer')
    if (!stored) throw new Error('No player session')
    const token = JSON.parse(stored).sessionToken
    const res = await fetch(`/api/games/${gId}/join`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', 'X-Session-Token': token },
    })
    if (!res.ok) throw new Error(`Join failed: ${res.status}`)
  }, gameId)
  await page2.goto(`/game/${gameId}`)
  await expect(page2.getByText('Waiting Room')).toBeVisible({ timeout: 10000 })

  // Start the game
  await expect(page1.getByText('2/4 players joined')).toBeVisible({ timeout: 15000 })
  await page1.getByRole('button', { name: /Start Game/ }).click()

  // Wait for game board on both pages
  await waitForGameBoard(page1)
  await waitForGameBoard(page2)

  return { page1, page2, gameId, context1, context2 }
}
