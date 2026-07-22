import { render, screen, waitFor } from "@testing-library/react";
import { axe } from "jest-axe";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { apiFetch } from "../api/client";
import { ApiError } from "../api/client";
import { PublicArtworkPage } from "./PublicArtworkPage";

vi.mock("../api/client", async (importOriginal) => ({
  ...(await importOriginal<typeof import("../api/client")>()),
  apiFetch: vi.fn(),
}));
const mockedApiFetch = vi.mocked(apiFetch);

describe("PublicArtworkPage", () => {
  const originalTitle = document.title;

  beforeEach(() => mockedApiFetch.mockReset());
  afterEach(() => { document.title = originalTitle; });

  it("renders one main landmark, artwork credit, ordered labeled descriptions, and a concise objective alt", async () => {
    mockedApiFetch.mockResolvedValueOnce({
      title: "Blue Study", credit: "A. Artist", imageUrl: "/public/artworks/blue-study/image", qrUrl: null,
      descriptions: [
        { label: "Subjective", text: "The composition feels expansive.",
          audioUrl: "/public/artworks/blue-study/descriptions/subjective/audio" },
        { label: "Objective", text: "A blue square rests near the center.",
          audioUrl: "/public/artworks/blue-study/descriptions/objective/audio" },
      ],
    });

    const { container } = render(<PublicArtworkPage slug="blue-study" />);

    expect(await screen.findByRole("heading", { level: 1, name: "Blue Study" })).toBeVisible();
    expect(screen.getAllByRole("main")).toHaveLength(1);
    expect(screen.getByText("A. Artist")).toBeVisible();
    expect(screen.getByRole("img", { name: "A blue square rests near the center." }))
      .toHaveAttribute("src", "/public/artworks/blue-study/image");
    expect(screen.getAllByRole("heading", { level: 2 }).map((heading) => heading.textContent))
      .toEqual(["Subjective", "Objective"]);
    expect(screen.getByLabelText("Listen to Subjective description for Blue Study"))
      .toHaveAttribute("src", "/public/artworks/blue-study/descriptions/subjective/audio");
    expect(screen.getByLabelText("Listen to Objective description for Blue Study"))
      .toHaveAttribute("preload", "none");
    expect(screen.getByText("The composition feels expansive.")).toBeVisible();
    expect(screen.getByText("A blue square rests near the center.")).toBeVisible();
    expect(document.title).toBe("Blue Study | CANVAS");
    expect(mockedApiFetch).toHaveBeenCalledWith("/public/artworks/blue-study");
    expect(await axe(container)).toHaveNoViolations();
  }, 15_000);

  it("uses a concise fallback instead of duplicating long objective prose", async () => {
    mockedApiFetch.mockResolvedValueOnce({
      title: "Blue Study", credit: "A. Artist", imageUrl: "/public/artworks/blue-study/image", qrUrl: null,
      descriptions: [{ label: "Objective", text: "A".repeat(161),
        audioUrl: "/public/artworks/blue-study/descriptions/objective/audio" }],
    });

    render(<PublicArtworkPage slug="blue-study" />);

    expect(await screen.findByRole("img", { name: "Artwork: Blue Study. Full descriptions follow." }))
      .toBeVisible();
  });

  it("uses the fallback alt when a label only contains the word objective", async () => {
    mockedApiFetch.mockResolvedValueOnce({
      title: "Blue Study", credit: "A. Artist", imageUrl: "/public/artworks/blue-study/image", qrUrl: null,
      descriptions: [{ label: "Non-objective response", text: "A concise but non-objective description.",
        audioUrl: "/public/artworks/blue-study/descriptions/response/audio/audio-asset" }],
    });

    render(<PublicArtworkPage slug="blue-study" />);

    expect(await screen.findByRole("img", { name: "Artwork: Blue Study. Full descriptions follow." }))
      .toBeVisible();
  });

  it("keeps an upgraded legacy description available as text when no audio was safely associated", async () => {
    mockedApiFetch.mockResolvedValueOnce({
      title: "Legacy Study", credit: "A. Artist", imageUrl: "/public/artworks/legacy-study/image", qrUrl: null,
      descriptions: [{ label: "Objective", text: "A blue square.", audioUrl: null }],
    });

    render(<PublicArtworkPage slug="legacy-study" />);

    expect(await screen.findByText("A blue square.")).toBeVisible();
    expect(screen.queryByLabelText("Listen to Objective description for Legacy Study")).not.toBeInTheDocument();
  });

  it("renders duplicate legacy description text without duplicate React keys", async () => {
    const errors = vi.spyOn(console, "error").mockImplementation(() => undefined);
    mockedApiFetch.mockResolvedValueOnce({
      title: "Legacy Study", credit: "A. Artist", imageUrl: "/public/artworks/legacy-study/image", qrUrl: null,
      descriptions: [
        { label: "Detail", text: "A repeated detail.", audioUrl: null },
        { label: "Detail", text: "A repeated detail.", audioUrl: null },
      ],
    });

    render(<PublicArtworkPage slug="legacy-study" />);

    expect(await screen.findAllByText("A repeated detail.")).toHaveLength(2);
    expect(errors.mock.calls.flat().join(" ")).not.toContain("same key");
    errors.mockRestore();
  });

  it("renders an accessible not-found error without leaking server details", async () => {
    mockedApiFetch.mockRejectedValueOnce(new ApiError(404, "Published artwork was not found."));

    const { container } = render(<PublicArtworkPage slug="missing" />);

    expect(await screen.findByRole("heading", { level: 1, name: "Artwork unavailable" })).toBeVisible();
    expect(screen.getByRole("alert")).toHaveTextContent("could not be found or is not currently published");
    await waitFor(() => expect(document.title).toBe("Artwork unavailable | CANVAS"));
    expect(await axe(container)).toHaveNoViolations();
  });

  it("distinguishes a temporary public-artwork outage from a missing publication", async () => {
    mockedApiFetch.mockRejectedValueOnce(new ApiError(503, "The published artwork is temporarily unavailable."));

    const { container } = render(<PublicArtworkPage slug="blue-study" />);

    expect(await screen.findByRole("heading", { level: 1, name: "Artwork temporarily unavailable" })).toBeVisible();
    expect(screen.getByRole("alert")).toHaveTextContent("temporarily unavailable");
    expect(screen.getByRole("alert")).not.toHaveTextContent("could not be found");
    expect(document.title).toBe("Artwork temporarily unavailable | CANVAS");
    expect(await axe(container)).toHaveNoViolations();
  });
});
