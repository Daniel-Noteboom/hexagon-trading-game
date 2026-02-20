import { test, expect } from '@playwright/test'
import { registerPlayer, createAndJoinGame, waitForGameBoard } from './helpers'

test.describe('Multi-Player Game Setup', () => {
  test('two players can join and start a game', async ({ browser, baseURL }) => {
    test.setTimeout(60000)

    // Create two isolated browser contexts with baseURL (separate sessions)
    const context1 = await browser.newContext({ baseURL: baseURL! })
    const context2 = await browser.newContext({ baseURL: baseURL! })
    const page1 = await context1.newPage()
    const page2 = await context2.newPage()

    // Player 1: register and create a game
    await registerPlayer(page1, `Host_${Date.now()}`)
    const gameId = await createAndJoinGame(page1)
    await expect(page1.getByText('Waiting Room')).toBeVisible({ timeout: 10000 })

    // Player 2: register, then join the specific game via API and navigate
    await registerPlayer(page2, `Joiner_${Date.now()}`)
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

    // Player 1: wait for player 2 to appear (WaitingRoom polls every 2s), then start
    await expect(page1.getByText('2/4 players joined')).toBeVisible({ timeout: 15000 })
    await page1.getByRole('button', { name: /Start Game/ }).click()

    // After starting, the page should transition from WaitingRoom to the game board
    await expect(page1.getByText('Waiting Room')).not.toBeVisible({ timeout: 15000 })

    // Both players should see the game board
    await waitForGameBoard(page1)
    await waitForGameBoard(page2)

    // Verify game content is rendered for both players
    await expect(page1.getByText('Resources')).toBeVisible()
    await expect(page2.getByText('Resources')).toBeVisible()

    await context1.close()
    await context2.close()
  })
})
