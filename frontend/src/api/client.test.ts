import { expect, it, vi } from "vitest";

import { apiFetch } from "./client";

it("preserves the problem field on API errors", async () => {
  vi.stubGlobal("fetch", vi.fn().mockResolvedValue(new Response(JSON.stringify({
    status: 400,
    title: "Invalid artwork upload",
    detail: "Title must be 255 characters or fewer.",
    code: "title_too_long",
    field: "title",
  }), { status: 400, headers: { "Content-Type": "application/problem+json" } })));

  await expect(apiFetch("/api/artworks")).rejects.toMatchObject({
    status: 400,
    code: "title_too_long",
    field: "title",
  });

  vi.unstubAllGlobals();
});
