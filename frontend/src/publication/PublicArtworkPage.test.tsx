import { render, screen, waitFor } from "@testing-library/react";
import { axe } from "jest-axe";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { apiFetch } from "../api/client";
import { PublicArtworkPage } from "./PublicArtworkPage";

vi.mock("../api/client", () => ({ apiFetch: vi.fn() }));
const mockedApiFetch = vi.mocked(apiFetch);

describe("PublicArtworkPage", () => {
  const originalTitle = document.title;

  beforeEach(() => mockedApiFetch.mockReset());
  afterEach(() => { document.title = originalTitle; });

  it("renders one main landmark, artwork credit, ordered labeled descriptions, and a concise objective alt", async () => {
    mockedApiFetch.mockResolvedValueOnce({
      title: "Blue Study", credit: "A. Artist", imageUrl: "/public/artworks/blue-study/image",
      descriptions: [
        { label: "Subjective", text: "The composition feels expansive." },
        { label: "Objective detail", text: "A blue square rests near the center." },
      ],
    });

    const { container } = render(<PublicArtworkPage slug="blue-study" />);

    expect(await screen.findByRole("heading", { level: 1, name: "Blue Study" })).toBeVisible();
    expect(screen.getAllByRole("main")).toHaveLength(1);
    expect(screen.getByText("A. Artist")).toBeVisible();
    expect(screen.getByRole("img", { name: "A blue square rests near the center." }))
      .toHaveAttribute("src", "/public/artworks/blue-study/image");
    expect(screen.getAllByRole("heading", { level: 2 }).map((heading) => heading.textContent))
      .toEqual(["Subjective", "Objective detail"]);
    expect(document.title).toBe("Blue Study | CANVAS");
    expect(mockedApiFetch).toHaveBeenCalledWith("/public/artworks/blue-study");
    expect(await axe(container)).toHaveNoViolations();
  });

  it("uses a concise fallback instead of duplicating long objective prose", async () => {
    mockedApiFetch.mockResolvedValueOnce({
      title: "Blue Study", credit: "A. Artist", imageUrl: "/public/artworks/blue-study/image",
      descriptions: [{ label: "Objective", text: "A".repeat(161) }],
    });

    render(<PublicArtworkPage slug="blue-study" />);

    expect(await screen.findByRole("img", { name: "Artwork: Blue Study. Full descriptions follow." }))
      .toBeVisible();
  });

  it("renders an accessible not-found error without leaking server details", async () => {
    mockedApiFetch.mockRejectedValueOnce(new Error("Published artwork was not found."));

    const { container } = render(<PublicArtworkPage slug="missing" />);

    expect(await screen.findByRole("heading", { level: 1, name: "Artwork unavailable" })).toBeVisible();
    expect(screen.getByRole("alert")).toHaveTextContent("could not be found or is not currently published");
    await waitFor(() => expect(document.title).toBe("Artwork unavailable | CANVAS"));
    expect(await axe(container)).toHaveNoViolations();
  });
});
