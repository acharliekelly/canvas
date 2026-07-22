import { expect, type Page } from "@playwright/test";
import { fileURLToPath } from "node:url";

export const ADMIN_USERNAME = process.env.CANVAS_E2E_ADMIN_USERNAME ?? "admin";
export const ADMIN_PASSWORD = process.env.CANVAS_E2E_ADMIN_PASSWORD ?? "password";
export const FIXTURE = fileURLToPath(new URL("fixtures/sample-artwork.png", import.meta.url));

export async function signIn(page: Page) {
  await page.goto("/");
  await page.getByLabel("Username").fill(ADMIN_USERNAME);
  await page.getByLabel("Password").fill(ADMIN_PASSWORD);
  await page.getByRole("button", { name: "Sign in" }).click();
  await expect(page.getByRole("heading", { name: "Artworks" })).toBeVisible();
}

export async function uploadArtwork(page: Page, title: string) {
  await page.getByLabel("Artwork image").setInputFiles(FIXTURE);
  await page.getByLabel("Title").fill(title);
  await page.getByLabel("Artist or display credit").fill("CANVAS acceptance fixture");
  await page.getByLabel("Editorial context").fill("Deterministic local acceptance data.");
  await page.getByRole("button", { name: "Upload artwork" }).click();
  await expect(page.getByText(`${title} uploaded`, { exact: true })).toBeVisible();
  await page.getByRole("link", { name: title }).first().click();
  await expect(page.getByRole("heading", { name: `Edit ${title}` })).toBeVisible();
}

export async function addDescription(page: Page, label: string, text: string) {
  await page.getByLabel("New description label").fill(label);
  await page.getByLabel("New description text").fill(text);
  await page.getByRole("button", { name: "Add description" }).click();
  await expect(page.getByText(`${label} description added`, { exact: true })).toBeVisible();
}

export async function approveDescription(page: Page, label: string) {
  await page.getByRole("button", { name: `Approve ${label}`, exact: true }).click();
  await expect(page.getByRole("dialog", { name: `Approve ${label} description` })).toBeVisible();
  await page.getByRole("button", { name: "Approve description" }).click();
  await expect(page.getByText(`${label} description approved`, { exact: true })).toBeVisible();
}

export function slugFor(title: string, artworkId: string) {
  const normalized = title.normalize("NFD").replace(/\p{M}+/gu, "").toLowerCase()
    .replace(/[^a-z0-9]+/g, "-").replace(/^-|-$/g, "") || "artwork";
  return `${normalized.slice(0, 180).replace(/-+$/g, "")}-${artworkId.toLowerCase()}`;
}
