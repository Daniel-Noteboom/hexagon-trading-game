import { test, expect, type Page } from '@playwright/test'
import {
  setupTwoPlayerGame,
  completeSetupPhase,
  getActivePlayerPage,
  rollDice,
  endTurn,
  waitForPhase,
  sendAction,
} from './helpers'

test.describe('Dice Rolling & Turn Flow', () => {
  test('players can roll dice and take turns', async ({ browser, baseURL }) => {
    test.setTimeout(180000)

    const { page1, page2, context1, context2 } = await setupTwoPlayerGame(browser, baseURL!)

    // Complete setup
    await completeSetupPhase(page1, page2)
    await waitForPhase(page1, 'MAIN', undefined, 20000)

    // Find who goes first
    let activePage = await getActivePlayerPage(page1, page2)
    const otherPage = activePage === page1 ? page2 : page1

    // Roll dice
    await rollDice(activePage)

    // Verify DiceDisplay shows two numbers — look for die elements
    // The DiceDisplay renders divs with die values inside
    // After rolling, the status should no longer be ROLL_DICE
    await expect(activePage.getByText(/= \d+/)).toBeVisible({ timeout: 5000 })

    // Handle potential robber/discard scenarios after a 7
    await handlePostRoll(activePage, otherPage)

    // Should now be in TRADE_BUILD — click End Turn
    await endTurn(activePage)

    // Other player should now have Roll Dice
    await expect(otherPage.getByRole('button', { name: 'Roll Dice' })).toBeVisible({ timeout: 15000 })

    // Do a couple more rounds
    for (let i = 0; i < 2; i++) {
      activePage = await getActivePlayerPage(page1, page2)
      const other = activePage === page1 ? page2 : page1
      await rollDice(activePage)
      await handlePostRoll(activePage, other)
      await endTurn(activePage)
    }

    await context1.close()
    await context2.close()
  })

  test('dice display shows correct total after roll', async ({ browser, baseURL }) => {
    test.setTimeout(180000)

    const { page1, page2, context1, context2 } = await setupTwoPlayerGame(browser, baseURL!)
    await completeSetupPhase(page1, page2)
    await waitForPhase(page1, 'MAIN', undefined, 20000)

    const activePage = await getActivePlayerPage(page1, page2)
    await rollDice(activePage)

    // Verify dice total is displayed (format: "= N" where N is 2-12)
    const totalText = await activePage.getByText(/= \d+/).textContent()
    expect(totalText).toBeTruthy()
    const total = parseInt(totalText!.replace('= ', ''))
    expect(total).toBeGreaterThanOrEqual(2)
    expect(total).toBeLessThanOrEqual(12)

    await context1.close()
    await context2.close()
  })
})

/**
 * After rolling dice, handle any robber/discard phases before TRADE_BUILD.
 */
async function handlePostRoll(activePage: Page, otherPage: Page) {
  // Wait briefly for state update
  await activePage.waitForTimeout(1000)

  // Check if we're in DISCARD phase (rolled a 7 and someone has >7 cards)
  const statusText = await activePage.locator('[style*="color: rgb(189, 195, 199)"]').textContent().catch(() => '')
  if (statusText?.includes('DISCARD')) {
    // Both players may need to discard
    for (const page of [activePage, otherPage]) {
      const discardBtn = page.getByRole('button', { name: 'Confirm Discard' })
      if (await discardBtn.isVisible({ timeout: 2000 }).catch(() => false)) {
        // Just submit with 0 discards if they have <=7 cards (shouldn't need to discard)
        // In real scenario, the discard UI requires selecting cards
        await discardBtn.click().catch(() => {})
      }
    }
    await activePage.waitForTimeout(1000)
  }

  // Check for ROBBER_MOVE phase
  if (statusText?.includes('ROBBER_MOVE')) {
    // Click a hex to move robber
    const hexes = activePage.locator('svg polygon')
    const count = await hexes.count()
    if (count > 0) {
      await hexes.nth(Math.floor(count / 2)).click()
      await activePage.waitForTimeout(1000)
    }
  }

  // Check for ROBBER_STEAL phase
  const stealBtn = activePage.getByRole('button', { name: /Steal from/ })
  if (await stealBtn.first().isVisible({ timeout: 2000 }).catch(() => false)) {
    await stealBtn.first().click()
    await activePage.waitForTimeout(1000)
  }

  // Wait for TRADE_BUILD phase
  await expect(activePage.getByRole('button', { name: 'End Turn' })).toBeVisible({ timeout: 15000 })
}
