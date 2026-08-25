import { defineConfig, devices } from '@playwright/test';

const browserChannel = process.env.E2E_BROWSER_CHANNEL?.trim();

export default defineConfig({
  testDir: './e2e',
  fullyParallel: false,
  workers: 1,
  retries: 0,
  reporter: 'list',
  outputDir: 'test-results/playwright',
  use: {
    baseURL: process.env.E2E_BASE_URL ?? 'http://127.0.0.1:3000',
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
    video: 'retain-on-failure',
  },
  projects: [{
    name: browserChannel ? `chromium-${browserChannel}` : 'chromium',
    use: {
      ...devices['Desktop Chrome'],
      ...(browserChannel ? { channel: browserChannel } : {}),
    },
  }],
});
