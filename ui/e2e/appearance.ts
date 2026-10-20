import { expect, type Page } from "@playwright/test";

/** Exercise the actual font, navigation and keyboard contract of the packaged UI. */
export async function verifyWorkspaceAppearance(page: Page) {
  await page.evaluate(async () => {
    await Promise.all(
      ["16px Geist", '12px "Geist Mono"', "32px Newsreader"].map((font) =>
        document.fonts.load(font),
      ),
    );
    await document.fonts.ready;
  });
  expect(await page.evaluate(() => document.fonts.check("32px Newsreader"))).toBe(true);
  expect((await page.request.get(new URL("favicon.svg", page.url()).href)).status()).toBe(200);
  const skip = page.getByRole("link", { name: "Skip to workspace" });
  await skip.focus();
  await expect(skip).toBeInViewport();
  await skip.press("Enter");
  await expect(page.getByRole("main")).toBeFocused();
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
}
