import { test, expect } from '@playwright/test'
import { registerPlayer, createAndJoinGame } from './helpers'

test.describe('Lobby Page', () => {
  test('shows lobby after registration', async ({ page }) => {
    await registerPlayer(page, `LobbyTest_${Date.now()}`)

    await expect(page.getByRole('heading', { name: 'Game Lobby' })).toBeVisible()
    await expect(page.getByRole('button', { name: 'Create New Game' })).toBeVisible()
  })

  test('creates a game and redirects to game page', async ({ page }) => {
    await registerPlayer(page, `Creator_${Date.now()}`)
    const gameId = await createAndJoinGame(page)

    expect(gameId).toBeTruthy()
    await expect(page.getByText('Waiting Room')).toBeVisible({ timeout: 10000 })
  })
})
