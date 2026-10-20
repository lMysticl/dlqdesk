import { test, expect } from "@playwright/test";
import { mkdir } from "node:fs/promises";
import path from "node:path";

test("repair a real dead letter, freeze it, pause and resume acknowledged replay", async ({
  page,
}, testInfo) => {
  const errors: string[] = [];
  page.on("pageerror", (error) => errors.push(error.message));
  await page.goto("./");
  await page.getByRole("button", { name: "Use the bundled local demo token" }).click();
  await page.getByRole("button", { name: "Connect workspace" }).click();
  const seed = page.getByRole("button", { name: "Add 12 demo records" });
  const seeded = page.waitForResponse(
    (response) => response.url().endsWith("/api/demo/seed") && response.status() === 200,
  );
  await seed.click();
  await seeded;
  await expect(seed).toBeEnabled();
  await expect(page.getByLabel("Partition").locator("option")).toHaveCount(2);
  const rangeResponse = page.waitForResponse(
    (response) => response.url().includes("/api/inbox?partition=1") && response.status() === 200,
  );
  await page.getByLabel("Partition").selectOption("1");
  const range = (await (await rangeResponse).json()) as { endOffset: string };
  await page.getByLabel("Jump to offset").fill((BigInt(range.endOffset) - 6n).toString());
  await page.getByRole("button", { name: "Go to offset" }).click();
  await expect(page.getByRole("checkbox")).toHaveCount(6);
  const malformed = page.getByRole("row").filter({ hasText: "REPAIR" }).first();
  await malformed.getByRole("button").click();
  await page
    .getByLabel("Replay payload · JSON")
    .fill('{"orderId":1045,"amount":49.90,"currency":"USD"}');
  await page.getByRole("button", { name: "Add to replay selection" }).click();
  for (const box of (await page.getByRole("checkbox").all()).slice(0, 6)) {
    if (!(await box.isChecked())) await box.check();
  }
  await page.getByLabel("Records / second").fill("1");
  await capture("workspace");
  await page.getByRole("button", { name: "Create preview" }).click();
  await page.getByRole("button", { name: "Start replay", exact: true }).click();
  await expect(page.getByRole("dialog")).toBeVisible();
  await page.keyboard.press("Escape");
  await expect(page.getByRole("dialog")).toBeHidden();
  await page.getByRole("button", { name: "Start replay", exact: true }).click();
  await page.getByRole("button", { name: "Confirm publish" }).click();
  await page.getByRole("button", { name: "Pause replay", exact: true }).click();
  await expect(page.getByRole("button", { name: "Resume pending" })).toBeEnabled();
  await page.getByRole("button", { name: "Resume pending" }).click();
  await page.getByRole("button", { name: "Confirm publish" }).click();
  const detail = page.locator(".replay-workspace > section").nth(1);
  await expect(detail.locator(".panel-heading .status").first()).toHaveText(/COMPLETED/i, {
    timeout: 30000,
  });
  expect(await detail.locator("tbody tr .status").allTextContents()).toEqual(
    expect.arrayContaining([expect.stringMatching(/SENT/i)]),
  );
  await capture("replay");
  await detail.locator("tbody tr").first().getByRole("button").click();
  await expect(
    page.getByRole("dialog").getByRole("heading", { name: "Output payload" }),
  ).toBeVisible();
  await page.keyboard.press("Escape");
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
  expect(errors).toEqual([]);

  async function capture(name: string) {
    const directory = process.env.CAPTURE_DIR;
    const file = directory
      ? path.join(directory, `dlqdesk-${name}-${testInfo.project.name}.png`)
      : testInfo.outputPath(`${name}.png`);
    await mkdir(path.dirname(file), { recursive: true });
    await page.screenshot({ path: file, fullPage: true });
    await testInfo.attach(name, { path: file, contentType: "image/png" });
  }
});
