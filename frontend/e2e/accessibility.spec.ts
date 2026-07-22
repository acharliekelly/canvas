import AxeBuilder from "@axe-core/playwright";
import { expect, test, type Page } from "@playwright/test";

import { addDescription, signIn, uploadArtwork } from "./helpers";

async function expectNoAxeViolations(page: Page) {
  const results = await new AxeBuilder({ page }).analyze();
  expect(results.violations).toEqual([]);
}

test("principal admin and public states have no detectable axe violations", async ({ page }) => {
  await page.goto("/");
  await expect(page.getByRole("heading", { name: "Administrator sign in" })).toBeVisible();
  await expectNoAxeViolations(page);

  await signIn(page);
  await expectNoAxeViolations(page);
  await page.getByRole("button", { name: "Upload artwork" }).click();
  await expect(page.getByRole("alert")).toContainText("Choose an artwork image");
  await expectNoAxeViolations(page);

  await uploadArtwork(page, "E2E Accessibility Study");
  await addDescription(page, "Objective", "A blue square on a cream field.");
  await addDescription(page, "Subjective", "The composition has a steady rhythm.");
  await expectNoAxeViolations(page);

  await page.getByRole("button", { name: "Approve Objective", exact: true }).click();
  await expectNoAxeViolations(page);
  await page.getByRole("button", { name: "Approve description" }).click();
  await expect(page.getByText("Objective description approved", { exact: true })).toBeVisible();

  await page.getByRole("button", { name: "Publish artwork" }).click();
  await expectNoAxeViolations(page);
  await page.getByRole("button", { name: "Confirm publication" }).click();
  await page.getByRole("link", { name: "Open published artwork" }).click();
  await expect(page.getByRole("heading", { name: "E2E Accessibility Study" })).toBeVisible();
  await expectNoAxeViolations(page);
});
