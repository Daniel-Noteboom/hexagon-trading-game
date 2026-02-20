import { test, expect } from '@playwright/test'
import { clearSession } from './helpers'

test.describe('Landing Page - Registration', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('/')
    await clearSession(page)
    await page.reload()
  })

  test('shows the landing page with title and input', async ({ page }) => {
    await expect(page.getByRole('heading', { name: 'Settlers of Catan' })).toBeVisible()
    await expect(page.getByPlaceholder('Your display name')).toBeVisible()
    await expect(page.getByRole('button', { name: 'Enter Game' })).toBeVisible()
  })

  test('button is disabled when name is empty', async ({ page }) => {
    await expect(page.getByRole('button', { name: 'Enter Game' })).toBeDisabled()
  })

  test('registers a player and redirects to lobby', async ({ page }) => {
    const name = `TestPlayer_${Date.now()}`
    await page.getByPlaceholder('Your display name').fill(name)
    await page.getByRole('button', { name: 'Enter Game' }).click()

    await page.waitForURL('/lobby')
    await expect(page.getByText(`Welcome, ${name}`)).toBeVisible()
  })
})
