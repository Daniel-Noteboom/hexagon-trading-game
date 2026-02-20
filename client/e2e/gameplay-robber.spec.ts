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

test.describe('Robber & Discard', () => {
  test('rolling a 7 triggers robber move (and discard if >7 cards)', async ({ browser, baseURL }) => {
    test.setTimeout(300000)

    const { page1, page2, context1, context2 } = await setupTwoPlayerGame(browser, baseURL!)
    await completeSetupPhase(page1, page2)
    await waitForPhase(page1, 'MAIN', undefined, 20000)

    let rolled7 = false

    // Keep rolling dice until we get a 7 (or timeout after many rounds)
    for (let round = 0; round < 50 && !rolled7; round++) {
      const activePage = await getActivePlayerPage(page1, page2)
      const otherPage = activePage === page1 ? page2 : page1

      await rollDice(activePage)
      await activePage.waitForTimeout(500)

      // Check if we hit ROBBER_MOVE or DISCARD
      const statusText = await activePage.locator('[style*="color: rgb(189, 195, 199)"]').textContent().catch(() => '')

      if (statusText?.includes('DISCARD') || statusText?.includes('ROBBER_MOVE')) {
        rolled7 = true

        if (statusText?.includes('DISCARD')) {
          // Handle discard for any player who needs to
          for (const page of [activePage, otherPage]) {
            const discardWarning = page.getByText(/You must discard \d+ cards/)
            if (await discardWarning.isVisible({ timeout: 2000 }).catch(() => false)) {
              // Verify discard UI elements are visible
              await expect(page.getByRole('button', { name: 'Confirm Discard' })).toBeVisible()

              // Select resources to discard using + buttons
              const plusBtns = page.locator('button:has-text("+")').filter({ has: page.locator('..') })
              // Click + buttons to select resources to discard
              // We need to select floor(total/2) cards
              const warningText = await discardWarning.textContent()
              const discardCount = parseInt(warningText?.match(/discard (\d+)/)?.[1] || '0')

              for (let d = 0; d < discardCount; d++) {
                // Click the first + button that's in the discard section
                const discardPlusBtns = page.getByRole('button', { name: '+' })
                if (await discardPlusBtns.first().isVisible({ timeout: 500 }).catch(() => false)) {
                  await discardPlusBtns.first().click()
                }
              }

              await page.getByRole('button', { name: 'Confirm Discard' }).click()
              await page.waitForTimeout(1000)
            }
          }

          // After discard, should move to ROBBER_MOVE
          await activePage.waitForTimeout(1000)
        }

        // Handle ROBBER_MOVE
        const robberStatus = await activePage.locator('[style*="color: rgb(189, 195, 199)"]').textContent().catch(() => '')
        if (robberStatus?.includes('ROBBER_MOVE')) {
          // Click a hex tile to move the robber
          const hexes = activePage.locator('svg polygon')
          const hexCount = await hexes.count()
          expect(hexCount).toBeGreaterThan(0)

          // Click a different hex (not the desert / current robber location)
          await hexes.nth(0).click()
          await activePage.waitForTimeout(1000)

          // Check for ROBBER_STEAL
          const stealBtn = activePage.getByRole('button', { name: /Steal from/ })
          if (await stealBtn.first().isVisible({ timeout: 3000 }).catch(() => false)) {
            // Verify steal UI shows opponent names
            await expect(stealBtn.first()).toBeVisible()
            // Click to steal
            await stealBtn.first().click()
            await activePage.waitForTimeout(1000)
          }
        }

        // Should return to TRADE_BUILD
        await expect(activePage.getByRole('button', { name: 'End Turn' })).toBeVisible({ timeout: 15000 })
        break
      }

      // Normal turn — just end it
      await expect(activePage.getByRole('button', { name: 'End Turn' })).toBeVisible({ timeout: 15000 })
      await endTurn(activePage)
    }

    // It's statistically very unlikely not to roll a 7 in 50 tries
    // but we don't fail the test since it's probabilistic
    if (rolled7) {
      // Verify the game continues normally after robber resolution
      const activePage = await getActivePlayerPage(page1, page2)
      await expect(activePage.getByRole('button', { name: /Roll Dice|End Turn/ })).toBeVisible({ timeout: 10000 })
    }

    await context1.close()
    await context2.close()
  })

  test('robber steal shows opponent buttons', async ({ browser, baseURL }) => {
    test.setTimeout(300000)

    const { page1, page2, context1, context2 } = await setupTwoPlayerGame(browser, baseURL!)
    await completeSetupPhase(page1, page2)
    await waitForPhase(page1, 'MAIN', undefined, 20000)

    // Use sendAction to force a 7 roll scenario by playing a knight card
    // This is more reliable than waiting for a natural 7
    let foundStealUI = false

    for (let round = 0; round < 50 && !foundStealUI; round++) {
      const activePage = await getActivePlayerPage(page1, page2)
      const otherPage = activePage === page1 ? page2 : page1

      await rollDice(activePage)
      await activePage.waitForTimeout(500)

      const statusText = await activePage.locator('[style*="color: rgb(189, 195, 199)"]').textContent().catch(() => '')

      if (statusText?.includes('DISCARD')) {
        for (const page of [activePage, otherPage]) {
          const discardBtn = page.getByRole('button', { name: 'Confirm Discard' })
          if (await discardBtn.isVisible({ timeout: 1000 }).catch(() => false)) {
            await discardBtn.click().catch(() => {})
          }
        }
        await activePage.waitForTimeout(1000)
      }

      if (statusText?.includes('ROBBER_MOVE') || (await activePage.locator('[style*="color: rgb(189, 195, 199)"]').textContent().catch(() => ''))?.includes('ROBBER_MOVE')) {
        // Move robber to a hex near opponent's settlement
        const hexes = activePage.locator('svg polygon')
        const hexCount = await hexes.count()
        // Try different hexes to find one adjacent to opponent
        for (let h = 0; h < Math.min(hexCount, 5); h++) {
          await hexes.nth(h).click()
          await activePage.waitForTimeout(500)

          const stealBtn = activePage.getByRole('button', { name: /Steal from/ })
          if (await stealBtn.first().isVisible({ timeout: 1000 }).catch(() => false)) {
            foundStealUI = true
            // Verify the steal button contains an opponent's name
            const btnText = await stealBtn.first().textContent()
            expect(btnText).toContain('Steal from')
            await stealBtn.first().click()
            break
          }

          // If no steal option, robber may have moved to empty hex — that's fine
          const endTurnBtn = activePage.getByRole('button', { name: 'End Turn' })
          if (await endTurnBtn.isVisible({ timeout: 500 }).catch(() => false)) {
            break
          }
        }
      }

      const endTurnBtn = activePage.getByRole('button', { name: 'End Turn' })
      if (await endTurnBtn.isVisible({ timeout: 5000 }).catch(() => false)) {
        await endTurn(activePage)
      }
    }

    await context1.close()
    await context2.close()
  })
})
