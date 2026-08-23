import { defineConfig, devices } from '@playwright/test';
import path from 'path';

const isExternalStack = process.env.E2E_EXTERNAL_STACK === '1';
const skipWebServer = process.env.E2E_SKIP_WEBSERVER === '1';
const apiBaseURL =
  process.env.E2E_API_URL ??
  (isExternalStack ? 'http://127.0.0.1/api/v1' : 'http://127.0.0.1:4010/api/v1');
const frontendBaseURL =
  process.env.E2E_BASE_URL ??
  (isExternalStack ? 'http://127.0.0.1' : 'http://127.0.0.1:3101');
const databaseUrl =
  process.env.E2E_DATABASE_URL ??
  'jdbc:postgresql://127.0.0.1:5433/campuscore_restful_e2e?currentSchema=thesis';
const frontendNodeOptions = [process.env.NODE_OPTIONS, '--max-old-space-size=4096']
  .filter(Boolean)
  .join(' ');

export default defineConfig({
  testDir: './e2e',
  fullyParallel: false,
  workers: 1,
  retries: 0,
  reporter: 'list',
  outputDir: 'test-results/playwright',
  use: {
    baseURL: frontendBaseURL,
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
    video: 'retain-on-failure',
  },
  projects: [
    {
      name: 'chromium',
      use: {
        ...devices['Desktop Chrome'],
      },
    },
  ],
  webServer: isExternalStack || skipWebServer
    ? undefined
    : [
        {
          command: 'mvn -q -f pom.xml -pl restful-api -am spring-boot:run',
          cwd: path.resolve(__dirname, '../java-services'),
          url: `${apiBaseURL}/health/liveness`,
          timeout: 120_000,
          reuseExistingServer: true,
          env: {
            ...process.env,
            SPRING_PROFILES_ACTIVE:
              process.env.E2E_SPRING_PROFILES_ACTIVE ?? 'persistence',
            SERVER_PORT: '4010',
            HEALTH_READINESS_KEY:
              process.env.E2E_HEALTH_READINESS_KEY ??
              'e2e-readiness-key-12345',
            INTERNAL_SERVICE_TOKEN:
              process.env.E2E_INTERNAL_SERVICE_TOKEN ??
              'e2e-internal-service-token-12345',
            JWT_SECRET: process.env.E2E_JWT_SECRET ?? 'e2e-jwt-secret',
            JWT_REFRESH_SECRET:
              process.env.E2E_JWT_REFRESH_SECRET ?? 'e2e-jwt-refresh-secret',
            JWT_ACCESS_TOKEN_TTL_SECONDS:
              process.env.E2E_JWT_ACCESS_TOKEN_TTL_SECONDS ?? '900',
            JWT_REFRESH_TOKEN_TTL_SECONDS:
              process.env.E2E_JWT_REFRESH_TOKEN_TTL_SECONDS ?? '604800',
            SWAGGER_ENABLED: process.env.E2E_SWAGGER_ENABLED ?? 'true',
            SPRING_DATASOURCE_URL: databaseUrl,
            SPRING_DATASOURCE_USERNAME:
              process.env.E2E_DATABASE_USER ?? 'campuscore',
            SPRING_DATASOURCE_PASSWORD:
              process.env.E2E_DATABASE_PASSWORD ?? 'campuscore_password',
          },
        },
        {
          command: 'npm run dev -- --hostname 127.0.0.1 --port 3101',
          cwd: __dirname,
          url: frontendBaseURL,
          timeout: 120_000,
          reuseExistingServer: true,
          env: {
            ...process.env,
            NEXT_PUBLIC_API_URL: apiBaseURL,
            NODE_OPTIONS: frontendNodeOptions,
          },
        },
      ],
});
