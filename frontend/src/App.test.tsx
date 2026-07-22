import { render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";

import App from "./App";

describe("App", () => {
  afterEach(() => {
    vi.unstubAllGlobals();
    window.history.replaceState({}, "", "/");
  });

  it("shows the application and backend status", async () => {
    vi.stubGlobal("fetch", vi.fn().mockImplementation((input: RequestInfo | URL) => {
      const body = String(input).endsWith("/api/session")
        ? { authenticated: false, username: null, csrfToken: "test-token" }
        : { status: "ready" };
      return Promise.resolve(new Response(JSON.stringify(body), { status: 200 }));
    }));
    render(<App />);

    expect(screen.getByRole("heading", { name: "CANVAS" })).toBeVisible();
    expect(await screen.findByText("System ready")).toBeVisible();
  });

  it("reports the backend as unavailable when its exact readiness endpoint returns an error", async () => {
    const fetch = vi.fn().mockResolvedValue(new Response("service unavailable", { status: 503 }));
    vi.stubGlobal("fetch", fetch);

    render(<App />);

    expect(await screen.findByText("Backend unavailable")).toBeVisible();
    expect(fetch).toHaveBeenCalledWith("/api/health", expect.objectContaining({ signal: expect.any(AbortSignal) }));
  });

  it("reports the backend as unavailable when the readiness payload is not exact", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(new Response(JSON.stringify({ status: "starting" }), { status: 200 })));

    render(<App />);

    expect(await screen.findByText("Backend unavailable")).toBeVisible();
  });

  it("renders the public unavailable state for a malformed encoded artwork slug", async () => {
    window.history.pushState({}, "", "/artworks/%E0%A4%A");

    expect(() => render(<App />)).not.toThrow();

    expect(await screen.findByRole("heading", { level: 1, name: "Artwork unavailable" })).toBeVisible();
  });

  it("rejects encoded path delimiters before making a public artwork request", async () => {
    window.history.pushState({}, "", "/artworks/..%2F..%2Fapi%2Fhealth");
    const fetch = vi.fn().mockRejectedValue(new Error("must not be requested"));
    vi.stubGlobal("fetch", fetch);

    render(<App />);

    expect(await screen.findByRole("heading", { level: 1, name: "Artwork unavailable" })).toBeVisible();
    expect(fetch).not.toHaveBeenCalled();
  });
});
