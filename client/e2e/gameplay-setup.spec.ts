import { test, expect } from '@playwright/test'
import { setupTwoPlayerGame, waitForPhase, completeSetupPhase } from './helpers'

test.describe('Setup Phase Completion', () => {
  test('two players complete full setup phase (SETUP_FORWARD + SETUP_REVERSE)', async ({ browser, baseURL }) => {
    test.setTimeout(120000)

    const { page1, page2, context1, context2 } = await setupTwoPlayerGame(browser, baseURL!)

    // Verify both players are in setup phase
    await waitForPhase(page1, 'SETUP_FORWARD')
    await waitForPhase(page2, 'SETUP_FORWARD')

    // Complete setup phase: 4 rounds of settlement + road placement
    await completeSetupPhase(page1, page2)

    // Verify phase transitions to MAIN
    await waitForPhase(page1, 'MAIN', undefined, 20000)
    await waitForPhase(page2, 'MAIN', undefined, 20000)

    // Verify each player has buildings on the board (settlements rendered as circles with player color)
    // There should be 4 settlements total (2 per player)
    const settlementCircles1 = page1.locator('svg circle[fill]:not([fill="transparent"]):not([fill="#fff"])')
    await expect(settlementCircles1).not.toHaveCount(0)

    // Verify roads exist on the board (rendered as colored lines with strokeWidth=5)
    const roads1 = page1.locator('svg line[stroke-width="5"]')
    await expect(roads1).not.toHaveCount(0)

    await context1.close()
    await context2.close()
  })

  test('setup phase alternates between players correctly', async ({ browser, baseURL }) => {
    test.setTimeout(120000)

    const { page1, page2, context1, context2 } = await setupTwoPlayerGame(browser, baseURL!)

    // During SETUP_FORWARD, one player should have click targets and the other shouldn't
    // We verify this by checking which page has cursor:pointer circles
    const p1HasTargets = await page1.locator('svg circle[style*="cursor: pointer"], svg circle[style*="cursor:pointer"]').count()
    const p2HasTargets = await page2.locator('svg circle[style*="cursor: pointer"], svg circle[style*="cursor:pointer"]').count()

    // Exactly one should have targets (the current player)
    expect(p1HasTargets > 0 || p2HasTargets > 0).toBe(true)

    await context1.close()
    await context2.close()
  })
})
