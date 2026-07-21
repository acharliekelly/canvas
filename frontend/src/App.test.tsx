import { render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";

import App from "./App";

describe("App", () => {
  afterEach(() => vi.unstubAllGlobals());

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
});
