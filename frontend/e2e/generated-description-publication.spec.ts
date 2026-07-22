import { expect, test } from "@playwright/test";

import { approveDescription, signIn, uploadArtwork } from "./helpers";

const TITLE = "E2E Generated Description Study";
const EDITED_TEXT = "A reviewed placeholder description for the generated acceptance journey.";

test("generated draft remains blocked until edited, approved, and explicitly published", async ({ page }) => {
  await signIn(page);
  await uploadArtwork(page, TITLE);
  await expect(page.getByRole("button", { name: "Publish artwork" })).toBeDisabled();
  await expect(page.getByText("Approve at least one description before publishing.")).toBeVisible();

  await page.getByRole("button", { name: "Generate placeholder draft" }).click();
  await expect(page.getByText("Placeholder draft added", { exact: true })).toBeVisible({ timeout: 15_000 });
  const generated = page.getByRole("region", { name: "Placeholder draft description" });
  await expect(generated).toContainText("Source: Generated");
  await expect(generated).toContainText("Status: Draft");
  await expect(page.getByRole("button", { name: "Publish artwork" })).toBeDisabled();
  await generated.getByLabel("Placeholder draft description text").fill(EDITED_TEXT);
  await generated.getByRole("button", { name: "Save Placeholder draft draft" }).click();
  await expect(page.getByText("Placeholder draft draft saved", { exact: true })).toBeVisible();
  await expect(page.getByRole("button", { name: "Publish artwork" })).toBeDisabled();
  await approveDescription(page, "Placeholder draft");

  await page.getByRole("button", { name: "Publish artwork" }).click();
  await page.getByRole("button", { name: "Confirm publication" }).click();
  await expect(page.getByText("Artwork published", { exact: true })).toBeVisible();
  await page.getByRole("link", { name: "Open published artwork" }).click();
  await expect(page.getByRole("heading", { name: TITLE })).toBeVisible();
  await expect(page.getByText(EDITED_TEXT)).toBeVisible();
  await expect(page.getByText(/deterministic placeholder description for/i)).toHaveCount(0);
});
