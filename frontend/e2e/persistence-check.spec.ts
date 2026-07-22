import { expect, test } from "@playwright/test";
import { readFile } from "node:fs/promises";

import { FIXTURE, signIn, slugFor } from "./helpers";

const TITLE = "E2E Manual Description Study";

test("published artwork and cached assets survive an ordinary Compose restart", async ({ page }) => {
  await signIn(page);
  const artworkLink = page.getByRole("link", { name: TITLE }).first();
  const editHref = await artworkLink.getAttribute("href");
  expect(editHref).toMatch(/^\/artworks\/[0-9a-f-]+\/edit$/);
  const artworkId = editHref?.split("/")[2] as string;
  const slug = slugFor(TITLE, artworkId);

  const publicResponse = await page.request.get(`/public/artworks/${slug}`);
  expect(publicResponse.ok()).toBe(true);
  const publicArtwork = await publicResponse.json();
  expect(publicArtwork.descriptions.map((item: { label: string }) => item.label)).toEqual(["Objective", "Subjective"]);
  expect(publicArtwork.imageUrl).toBe(`/public/artworks/${slug}/image`);
  const originalImage = await page.request.get(publicArtwork.imageUrl);
  expect(originalImage.ok()).toBe(true);
  expect(originalImage.headers()["content-type"]).toContain("image/png");
  const imageBytes = await originalImage.body();
  expect([...imageBytes.subarray(0, 8)]).toEqual([137, 80, 78, 71, 13, 10, 26, 10]);
  expect(imageBytes.equals(await readFile(FIXTURE))).toBe(true);
  for (const description of publicArtwork.descriptions as Array<{ audioUrl: string }>) {
    const audio = await page.request.get(description.audioUrl);
    expect(audio.ok()).toBe(true);
    expect(audio.headers()["cache-control"]).toContain("immutable");
    expect((await audio.body()).subarray(0, 4).toString("ascii")).toBe("RIFF");
  }
  const qr = await page.request.get(`/public/artworks/${slug}/qr`);
  expect(qr.ok()).toBe(true);
  expect(qr.headers()["cache-control"]).toContain("immutable");
  expect([...(await qr.body()).subarray(0, 8)]).toEqual([137, 80, 78, 71, 13, 10, 26, 10]);
});
