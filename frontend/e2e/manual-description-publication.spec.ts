import { expect, test } from "@playwright/test";

import { addDescription, approveDescription, signIn, uploadArtwork } from "./helpers";

const TITLE = "E2E Manual Description Study";
const OBJECTIVE = "A blue square sits above two narrow gold lines.";
const SUBJECTIVE = "The repeated geometry creates a measured visual rhythm.";
const UNPUBLISHED_DRAFT_CANARY = "UNPUBLISHED-DRAFT-CANARY-7b35143f must remain private.";

test("administrator publishes ordered manual descriptions with cached audio and QR", async ({ page, browser }) => {
  await signIn(page);
  await uploadArtwork(page, TITLE);

  await addDescription(page, "Subjective", SUBJECTIVE);
  await addDescription(page, "Objective", OBJECTIVE);
  await page.getByRole("button", { name: "Move Objective up" }).click();
  await expect(page.getByText("Description order saved", { exact: true })).toBeVisible();
  await approveDescription(page, "Objective");
  await approveDescription(page, "Subjective");

  await page.getByRole("button", { name: "Publish artwork" }).click();
  await expect(page.getByRole("dialog", { name: "Publish this artwork?" })).toBeVisible();
  await page.getByRole("button", { name: "Confirm publication" }).click();
  await expect(page.getByText("Artwork published", { exact: true })).toBeVisible();

  const publicLink = page.getByRole("link", { name: "Open published artwork" });
  const publicHref = await publicLink.getAttribute("href");
  expect(publicHref).toMatch(/^\/artworks\/e2e-manual-description-study-/);

  const qrDownload = page.waitForEvent("download");
  await page.getByRole("link", { name: `Download QR code for ${TITLE}` }).click();
  const download = await qrDownload;
  expect(download.suggestedFilename()).toMatch(/-qr\.png$/);
  const downloadPath = await download.path();
  expect(downloadPath).not.toBeNull();
  const qrBytes = await import("node:fs/promises").then((fs) => fs.readFile(downloadPath as string));
  expect([...qrBytes.subarray(0, 8)]).toEqual([137, 80, 78, 71, 13, 10, 26, 10]);

  await addDescription(page, "Private review", UNPUBLISHED_DRAFT_CANARY);
  await expect(page.getByRole("region", { name: "Private review description" })).toContainText("Status: Draft");

  const anonymousContext = await browser.newContext();
  const publicPage = await anonymousContext.newPage();
  const anonymousSession = await publicPage.request.get("/api/session");
  expect(anonymousSession.ok()).toBe(true);
  expect(await anonymousSession.json()).toMatchObject({ authenticated: false, username: null });
  await publicPage.goto(publicHref as string);
  await expect(publicPage.getByRole("heading", { name: TITLE })).toBeVisible();
  const descriptions = publicPage.locator(".published-descriptions > section");
  await expect(descriptions).toHaveCount(2);
  await expect(descriptions.nth(0).getByRole("heading")).toHaveText("Objective");
  await expect(descriptions.nth(0)).toContainText(OBJECTIVE);
  await expect(descriptions.nth(1).getByRole("heading")).toHaveText("Subjective");
  await expect(descriptions.nth(1)).toContainText(SUBJECTIVE);
  await expect(publicPage.getByText(UNPUBLISHED_DRAFT_CANARY, { exact: true })).toHaveCount(0);
  await expect(publicPage.getByLabel(`Listen to Objective description for ${TITLE}`)).toHaveAttribute("src", /audio$/);

  const publicResponse = await publicPage.request.get(`/public${publicHref?.replace("/artworks", "/artworks")}`);
  const publicJson = await publicResponse.json();
  expect(publicJson.descriptions.map((description: { text: string }) => description.text)).toEqual([
    OBJECTIVE,
    SUBJECTIVE,
  ]);
  expect(JSON.stringify(publicJson)).not.toContain(UNPUBLISHED_DRAFT_CANARY);
  expect(JSON.stringify(publicJson)).not.toMatch(/draft|approvedBy|objectKey|administrator|stack/i);

  const audioUrl = await publicPage.getByLabel(`Listen to Objective description for ${TITLE}`).getAttribute("src");
  const firstAudio = await publicPage.request.get(audioUrl as string);
  const firstQr = await publicPage.request.get(`${publicHref?.replace("/artworks/", "/public/artworks/")}/qr`);
  expect(firstAudio.headers()["content-type"]).toContain("audio/wav");
  expect((await firstAudio.body()).subarray(0, 4).toString("ascii")).toBe("RIFF");

  await page.getByRole("button", { name: "Publish artwork" }).click();
  await page.getByRole("button", { name: "Confirm publication" }).click();
  await expect(page.getByText("Published artwork is already up to date", { exact: true })).toBeVisible();
  const secondAudio = await publicPage.request.get(audioUrl as string);
  const secondQr = await publicPage.request.get(`${publicHref?.replace("/artworks/", "/public/artworks/")}/qr`);
  expect(secondAudio.headers().etag).toBe(firstAudio.headers().etag);
  expect(secondQr.headers().etag).toBe(firstQr.headers().etag);
  await anonymousContext.close();
});
