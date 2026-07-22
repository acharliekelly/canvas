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
  const publicLink = page.getByRole("link", { name: "Open published artwork" });
  const publicHref = await publicLink.getAttribute("href");
  expect(publicHref).toMatch(/^\/artworks\/e2e-generated-description-study-/);
  const slug = publicHref?.replace("/artworks/", "") as string;
  const qrLink = page.getByRole("link", { name: `Download QR code for ${TITLE}` });
  await expect(qrLink).toHaveAttribute("href", new RegExp(`^/public/artworks/${slug}/qr/[0-9a-f-]+$`));

  const qrDownload = page.waitForEvent("download");
  await qrLink.click();
  const download = await qrDownload;
  expect(download.suggestedFilename()).toBe(`${slug}-qr.png`);
  const downloadPath = await download.path();
  expect(downloadPath).not.toBeNull();
  const qrBytes = await import("node:fs/promises").then((fs) => fs.readFile(downloadPath as string));
  expect([...qrBytes.subarray(0, 8)]).toEqual([137, 80, 78, 71, 13, 10, 26, 10]);

  await publicLink.click();
  await expect(page.getByRole("heading", { name: TITLE })).toBeVisible();
  await expect(page.getByText(EDITED_TEXT)).toBeVisible();
  await expect(page.getByText(/deterministic placeholder description for/i)).toHaveCount(0);
  const audio = page.getByLabel(`Listen to Placeholder draft description for ${TITLE}`);
  await expect(audio).toBeVisible();
  await expect(audio).toHaveAttribute("controls", "");
  const audioUrl = await audio.getAttribute("src");
  expect(audioUrl).toMatch(new RegExp(`^/public/artworks/${slug}/descriptions/[0-9a-f-]+/audio/[0-9a-f-]+$`));
  const audioResponse = await page.request.get(audioUrl as string);
  expect(audioResponse.ok()).toBe(true);
  expect(audioResponse.headers()["content-type"]).toContain("audio/wav");
  const audioBytes = await audioResponse.body();
  expect(audioBytes.length).toBeGreaterThan(44);
  expect(audioBytes.subarray(0, 4).toString("ascii")).toBe("RIFF");
  expect(audioBytes.subarray(8, 12).toString("ascii")).toBe("WAVE");
});
