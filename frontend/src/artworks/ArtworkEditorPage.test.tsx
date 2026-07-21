import { render, screen } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { apiFetch } from "../api/client";
import { ArtworkEditorPage } from "./ArtworkEditorPage";

vi.mock("../api/client", () => ({ apiFetch: vi.fn() }));
const mockedApiFetch = vi.mocked(apiFetch);

describe("ArtworkEditorPage", () => {
  beforeEach(() => mockedApiFetch.mockReset());

  it("loads artwork details and an empty ordered description editor", async () => {
    mockedApiFetch
      .mockResolvedValueOnce({
        id: "artwork-1", title: "Blue Study", credit: "A. Artist", context: null,
        status: "UPLOADED", mediaType: "image/png", byteSize: 100, version: 3,
        createdAt: "2026-07-21T12:00:00Z", updatedAt: "2026-07-21T12:00:00Z",
      })
      .mockResolvedValueOnce([]);

    render(<ArtworkEditorPage artworkId="artwork-1" />);

    expect(await screen.findByRole("heading", { name: "Edit Blue Study" })).toBeVisible();
    expect(screen.getByText("A. Artist")).toBeVisible();
    expect(screen.getByText("No descriptions yet.")).toBeVisible();
    expect(mockedApiFetch).toHaveBeenCalledWith("/api/artworks/artwork-1/descriptions");
  });

  it("shows an actionable load failure", async () => {
    mockedApiFetch.mockRejectedValueOnce(new Error("offline")).mockResolvedValueOnce([]);

    render(<ArtworkEditorPage artworkId="artwork-1" />);

    expect(await screen.findByRole("alert")).toHaveTextContent("The artwork editor could not be loaded");
  });
});
