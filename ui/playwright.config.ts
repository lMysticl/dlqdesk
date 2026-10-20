import { defineConfig, devices } from "@playwright/test";
export default defineConfig({
  testDir: "./e2e",
  timeout: 60000,
  expect: { timeout: 15000 },
  workers: 1,
  fullyParallel: false,
  reporter: [
    ["list"],
    ["json", { outputFile: process.env.E2E_REPORT ?? "test-results/report.json" }],
  ],
  metadata: {
    artifactSha256: process.env.E2E_ARTIFACT_SHA ?? "CI packaged artifact",
    sourceCommit: process.env.GITHUB_SHA ?? "local",
  },
  use: {
    baseURL: process.env.E2E_BASE_URL ?? "http://127.0.0.1:8092/",
    headless: true,
    locale: "en-US",
    timezoneId: "UTC",
    trace: "retain-on-failure",
  },
  projects: [
    { name: "desktop", use: { viewport: { width: 1440, height: 1000 } } },
    { name: "mobile", use: { ...devices["Pixel 7"], browserName: "chromium" } },
  ],
  webServer: process.env.E2E_BASE_URL
    ? undefined
    : {
        command:
          "java -Xmx512m -jar ../target/dlqdesk-0.1.0.jar --spring.datasource.url=jdbc:h2:mem:browser-contract --spring.profiles.active=demo --dlqdesk.bootstrap-servers=localhost:59092",
        url: "http://127.0.0.1:8092/",
        reuseExistingServer: false,
        timeout: 45000,
      },
});
