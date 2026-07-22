import { expect, test } from "@playwright/test";

import { ADMIN_PASSWORD, ADMIN_USERNAME } from "./helpers";

test("sign-in and upload error recovery are operable with the keyboard", async ({ page }) => {
  await page.goto("/");
  await expect(page.getByRole("heading", { name: "Administrator sign in" })).toBeVisible();

  await page.keyboard.press("Tab");
  await expect(page.getByLabel("Username")).toBeFocused();
  await page.keyboard.type(ADMIN_USERNAME);

  await page.keyboard.press("Tab");
  await expect(page.getByLabel("Password")).toBeFocused();
  await page.keyboard.type(ADMIN_PASSWORD);

  await page.keyboard.press("Tab");
  await expect(page.getByRole("button", { name: "Sign in" })).toBeFocused();
  await page.keyboard.press("Enter");
  await expect(page.getByRole("heading", { name: "Artworks" })).toBeVisible();

  await page.keyboard.press("Tab");
  await expect(page.getByLabel("Artwork image")).toBeFocused();
  await page.keyboard.press("Tab");
  await expect(page.getByLabel("Title")).toBeFocused();
  await page.keyboard.press("Tab");
  await expect(page.getByLabel("Artist or display credit")).toBeFocused();
  await page.keyboard.press("Tab");
  await expect(page.getByLabel("Editorial context")).toBeFocused();
  await page.keyboard.press("Tab");
  await expect(page.getByRole("button", { name: "Upload artwork" })).toBeFocused();
  await page.keyboard.press("Enter");

  const errorSummary = page.getByRole("alert");
  await expect(errorSummary).toBeFocused();
  await expect(errorSummary).toContainText("Choose an artwork image");
  await page.keyboard.press("Tab");
  await expect(page.getByRole("link", { name: "Choose an artwork image" })).toBeFocused();
  await page.keyboard.press("Enter");
  await expect(page.getByLabel("Artwork image")).toBeFocused();
});
