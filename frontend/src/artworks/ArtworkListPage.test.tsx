import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { apiFetch } from "../api/client";
import { ArtworkListPage } from "./ArtworkListPage";

vi.mock("../api/client", () => ({ apiFetch: vi.fn() }));
const mockedApiFetch = vi.mocked(apiFetch);
const validPng = new File([new Uint8Array([137, 80, 78, 71])], "study.png", { type: "image/png" });

describe("ArtworkListPage", () => {
  beforeEach(() => { mockedApiFetch.mockReset(); });

  it("renders the persistent artwork list with status and edit links", async () => {
    mockedApiFetch.mockResolvedValueOnce([{ id: "saved", title: "Saved Study", credit: "A. Artist", status: "UPLOADED", createdAt: "now" }]);
    render(<ArtworkListPage />);

    expect(await screen.findByRole("link", { name: /Saved Study/ })).toHaveAttribute("href", "/artworks/saved/edit");
    expect(screen.getByText("A. Artist")).toBeVisible();
    expect(screen.getByText("UPLOADED")).toBeVisible();
  });

  it("announces a completed upload and adds the artwork", async () => {
    const user = userEvent.setup();
    mockedApiFetch
      .mockResolvedValueOnce([])
      .mockResolvedValueOnce({ id: "new", title: "Blue Study", credit: "A. Artist", status: "UPLOADED", createdAt: "now" });
    render(<ArtworkListPage />);

    await screen.findByText("No artworks uploaded yet.");
    await user.upload(screen.getByLabelText("Artwork image"), validPng);
    await user.type(screen.getByLabelText("Title"), "Blue Study");
    await user.type(screen.getByLabelText("Artist or display credit"), "A. Artist");
    await user.click(screen.getByRole("button", { name: "Upload artwork" }));

    expect(await screen.findByRole("status")).toHaveTextContent("Blue Study uploaded");
    expect(screen.getByRole("link", { name: /Blue Study/ })).toBeVisible();
  });
});
