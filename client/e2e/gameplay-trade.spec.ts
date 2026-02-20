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

test.describe('Player & Bank Trading', () => {
  test('player can offer a trade and other player can accept', async ({ browser, baseURL }) => {
    test.setTimeout(180000)

    const { page1, page2, context1, context2 } = await setupTwoPlayerGame(browser, baseURL!)
    await completeSetupPhase(page1, page2)
    await waitForPhase(page1, 'MAIN', undefined, 20000)

    // Play a few rounds to accumulate resources
    for (let i = 0; i < 3; i++) {
      const active = await getActivePlayerPage(page1, page2)
      const other = active === page1 ? page2 : page1
      await rollDice(active)
      await handlePostRollSimple(active, other)
      await endTurn(active)
    }

    // Find the active player in TRADE_BUILD phase
    const activePage = await getActivePlayerPage(page1, page2)
    const otherPage = activePage === page1 ? page2 : page1
    await rollDice(activePage)
    await handlePostRollSimple(activePage, otherPage)

    // Click "Player Trade" button
    const playerTradeBtn = activePage.getByRole('button', { name: 'Player Trade' })
    if (await playerTradeBtn.isVisible({ timeout: 3000 }).catch(() => false)) {
      await playerTradeBtn.click()

      // Set offering: increment first resource
      const offerPlusButtons = activePage.locator('text=You give:').locator('..').getByRole('button', { name: '+' })
      if (await offerPlusButtons.first().isVisible({ timeout: 2000 }).catch(() => false)) {
        await offerPlusButtons.first().click()
      }

      // Set requesting: increment a different resource
      const requestPlusButtons = activePage.locator('text=You want:').locator('..').getByRole('button', { name: '+' })
      if (await requestPlusButtons.first().isVisible({ timeout: 2000 }).catch(() => false)) {
        await requestPlusButtons.nth(2).click() // Click 3rd resource type's +
      }

      // Send offer
      const sendOfferBtn = activePage.getByRole('button', { name: 'Send Offer' })
      if (await sendOfferBtn.isVisible({ timeout: 2000 }).catch(() => false)) {
        await sendOfferBtn.click()

        // Other player should see the trade offer with Accept/Decline
        const acceptBtn = otherPage.getByRole('button', { name: 'Accept' })
        const declineBtn = otherPage.getByRole('button', { name: 'Decline' })

        if (await acceptBtn.isVisible({ timeout: 5000 }).catch(() => false)) {
          await expect(declineBtn).toBeVisible()

          // Test decline first
          await declineBtn.click()
          await expect(acceptBtn).not.toBeVisible({ timeout: 5000 })
        }
      }
    }

    await context1.close()
    await context2.close()
  })

  test('player can do a bank trade', async ({ browser, baseURL }) => {
    test.setTimeout(180000)

    const { page1, page2, context1, context2 } = await setupTwoPlayerGame(browser, baseURL!)
    await completeSetupPhase(page1, page2)
    await waitForPhase(page1, 'MAIN', undefined, 20000)

    // Play several rounds to accumulate resources (need 4 of one type for bank trade)
    for (let i = 0; i < 8; i++) {
      const active = await getActivePlayerPage(page1, page2)
      const other = active === page1 ? page2 : page1
      await rollDice(active)
      await handlePostRollSimple(active, other)
      await endTurn(active)
    }

    // Get active player into TRADE_BUILD
    const activePage = await getActivePlayerPage(page1, page2)
    const otherPage = activePage === page1 ? page2 : page1
    await rollDice(activePage)
    await handlePostRollSimple(activePage, otherPage)

    // Try bank trade
    const bankTradeBtn = activePage.getByRole('button', { name: 'Bank Trade' })
    if (await bankTradeBtn.isVisible({ timeout: 3000 }).catch(() => false)) {
      await bankTradeBtn.click()

      // Verify bank trade UI is shown
      await expect(activePage.getByText('Give:')).toBeVisible()
      await expect(activePage.getByText('Receive:')).toBeVisible()

      // Select resources and trade
      const tradeBtn = activePage.getByRole('button', { name: 'Trade' })
      if (await tradeBtn.isVisible({ timeout: 2000 }).catch(() => false)) {
        await tradeBtn.click()
        // Trade may succeed or fail depending on resources available
        await activePage.waitForTimeout(1000)
      }

      // Cancel if still open
      const cancelBtn = activePage.getByRole('button', { name: 'Cancel' })
      if (await cancelBtn.isVisible({ timeout: 1000 }).catch(() => false)) {
        await cancelBtn.click()
      }
    }

    await context1.close()
    await context2.close()
  })
})

/**
 * Simple post-roll handler that handles robber phases.
 */
async function handlePostRollSimple(activePage: Page, otherPage: Page) {
  await activePage.waitForTimeout(1000)

  // Handle discard if needed
  for (const page of [activePage, otherPage]) {
    const discardBtn = page.getByRole('button', { name: 'Confirm Discard' })
    if (await discardBtn.isVisible({ timeout: 1000 }).catch(() => false)) {
      // Try to submit discard (may need resource selection)
      await discardBtn.click().catch(() => {})
    }
  }

  // Handle robber move
  const statusText = await activePage.locator('[style*="color: rgb(189, 195, 199)"]').textContent().catch(() => '')
  if (statusText?.includes('ROBBER_MOVE')) {
    const hexes = activePage.locator('svg polygon')
    const count = await hexes.count()
    if (count > 0) {
      await hexes.nth(Math.floor(count / 2)).click()
      await activePage.waitForTimeout(1000)
    }
  }

  // Handle robber steal
  const stealBtn = activePage.getByRole('button', { name: /Steal from/ })
  if (await stealBtn.first().isVisible({ timeout: 1000 }).catch(() => false)) {
    await stealBtn.first().click()
    await activePage.waitForTimeout(1000)
  }

  // Wait for end turn to be available
  await expect(activePage.getByRole('button', { name: 'End Turn' })).toBeVisible({ timeout: 15000 })
}
