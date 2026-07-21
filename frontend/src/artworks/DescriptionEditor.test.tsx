import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { axe } from "jest-axe";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { apiFetch } from "../api/client";
import type { DescriptionResponse } from "../api/types";
import { DescriptionEditor } from "./DescriptionEditor";

vi.mock("../api/client", () => ({ apiFetch: vi.fn() }));
const mockedApiFetch = vi.mocked(apiFetch);

describe("DescriptionEditor", () => {
  beforeEach(() => mockedApiFetch.mockReset());

  it("has no automated accessibility violations with zero descriptions", async () => {
    const { container } = render(
      <DescriptionEditor artworkId="artwork-1" artworkVersion={0} initialDescriptions={[]} />,
    );

    expect(screen.getByText("No descriptions yet.")).toBeVisible();
    expect(await axe(container)).toHaveNoViolations();
  });

  it("requires both free-form label and text and keeps the error state accessible", async () => {
    const user = userEvent.setup();
    const { container } = render(
      <DescriptionEditor artworkId="artwork-1" artworkVersion={0} initialDescriptions={[]} />,
    );

    await user.click(screen.getByRole("button", { name: "Add description" }));

    expect(screen.getByRole("alert")).toHaveTextContent("Enter a description label");
    expect(screen.getByLabelText("New description label")).toHaveAttribute("aria-invalid", "true");
    expect(await axe(container)).toHaveNoViolations();
  });

  it("adds multiple cards with organization-defined labels", async () => {
    const user = userEvent.setup();
    mockedApiFetch
      .mockResolvedValueOnce(description("objective", "Objective", "A blue square.", 0))
      .mockResolvedValueOnce(description("subjective", "Subjective", "It feels expansive.", 1));
    render(<DescriptionEditor artworkId="artwork-1" artworkVersion={0} initialDescriptions={[]} />);

    await addDescription(user, "Objective", "A blue square.");
    expect(await screen.findByRole("region", { name: "Objective description" })).toBeVisible();
    await addDescription(user, "Subjective", "It feels expansive.");

    expect(screen.getByRole("region", { name: "Objective description" })).toBeVisible();
    expect(screen.getByRole("region", { name: "Subjective description" })).toBeVisible();
    expect(mockedApiFetch).toHaveBeenNthCalledWith(1, "/api/artworks/artwork-1/descriptions", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ label: "Objective", text: "A blue square." }),
    });
  });

  it("saves draft changes without replacing the card", async () => {
    const user = userEvent.setup();
    const initial = description("objective", "Objective", "A blue square.", 0);
    mockedApiFetch.mockResolvedValueOnce({
      ...initial,
      version: 2,
      currentRevision: { ...initial.currentRevision, text: "A cobalt square." },
    });
    render(<DescriptionEditor artworkId="artwork-1" artworkVersion={0} initialDescriptions={[initial]} />);

    const text = screen.getByLabelText("Objective description text");
    await user.clear(text);
    await user.type(text, "A cobalt square.");
    await user.click(screen.getByRole("button", { name: "Save Objective draft" }));

    expect(await screen.findByRole("status")).toHaveTextContent("Objective draft saved");
    expect(mockedApiFetch).toHaveBeenCalledWith(
      "/api/artworks/artwork-1/descriptions/objective/draft",
      expect.objectContaining({ body: JSON.stringify({ label: "Objective", text: "A cobalt square.", version: 1 }) }),
    );
  });

  it("moves cards by buttons, disables boundaries, and preserves field values", async () => {
    const user = userEvent.setup();
    const objective = description("objective", "Objective", "A blue square.", 0);
    const subjective = description("subjective", "Subjective", "It feels expansive.", 1);
    mockedApiFetch.mockResolvedValueOnce([{ ...subjective, displayOrder: 0 }, { ...objective, displayOrder: 1 }]);
    render(
      <DescriptionEditor artworkId="artwork-1" artworkVersion={4}
        initialDescriptions={[objective, subjective]} />,
    );

    expect(screen.getByRole("button", { name: "Move Objective up" })).toBeDisabled();
    expect(screen.getByRole("button", { name: "Move Subjective down" })).toBeDisabled();
    await user.type(screen.getByLabelText("Objective description text"), " Kept locally.");
    await user.click(screen.getByRole("button", { name: "Move Objective down" }));

    const regions = screen.getAllByRole("region").filter((region) => region.hasAttribute("aria-label"));
    expect(regions.map((region) => region.getAttribute("aria-label")))
      .toEqual(["Subjective description", "Objective description"]);
    expect(screen.getByLabelText("Objective description text")).toHaveValue("A blue square. Kept locally.");
    expect(mockedApiFetch).toHaveBeenCalledWith("/api/artworks/artwork-1/description-order",
      expect.objectContaining({ body: JSON.stringify({ descriptionIds: ["subjective", "objective"], version: 4 }) }));
  });

  it("requires explicit confirmation and announces approval audit status", async () => {
    const user = userEvent.setup();
    const objective = description("objective", "Objective", "A blue square.", 0);
    mockedApiFetch.mockResolvedValueOnce(approvedDescription(objective));
    render(<DescriptionEditor artworkId="artwork-1" artworkVersion={0} initialDescriptions={[objective]} />);

    await user.click(screen.getByRole("button", { name: "Approve Objective" }));

    const dialog = screen.getByRole("dialog", { name: "Approve Objective description" });
    expect(dialog).toHaveTextContent("Later edits create a new draft");
    expect(mockedApiFetch).not.toHaveBeenCalled();
    await user.click(within(dialog).getByRole("button", { name: "Approve description" }));

    expect((await screen.findAllByText(/Approved by configured-admin/))[0]).toBeVisible();
    expect(mockedApiFetch).toHaveBeenCalledWith(
      "/api/artworks/artwork-1/descriptions/objective/approve",
      expect.objectContaining({ method: "POST", body: JSON.stringify({ version: 1 }) }),
    );
  });

  it("explains that editing approved content creates a new draft and retains history", async () => {
    const user = userEvent.setup();
    const approved = approvedDescription(description("objective", "Objective", "A blue square.", 0));
    const draft = {
      ...approved,
      version: 3,
      currentRevision: revision("draft-2", "Objective", "A cobalt square.", "DRAFT", "revision-objective"),
      revisions: [approved.currentRevision,
        revision("draft-2", "Objective", "A cobalt square.", "DRAFT", "revision-objective")],
    } satisfies DescriptionResponse;
    mockedApiFetch.mockResolvedValueOnce(draft);
    render(<DescriptionEditor artworkId="artwork-1" artworkVersion={0} initialDescriptions={[approved]} />);

    const text = screen.getByLabelText("Objective description text");
    await user.clear(text);
    await user.type(text, "A cobalt square.");
    await user.click(screen.getByRole("button", { name: "Save Objective draft" }));

    expect(await screen.findByText("New draft based on an approved revision. Earlier approved text remains in history."))
      .toBeVisible();
    expect(screen.getByText("A blue square.")).toBeVisible();
  });
});

async function addDescription(user: ReturnType<typeof userEvent.setup>, label: string, text: string) {
  await user.type(screen.getByLabelText("New description label"), label);
  await user.type(screen.getByLabelText("New description text"), text);
  await user.click(screen.getByRole("button", { name: "Add description" }));
}

function description(id: string, label: string, text: string, displayOrder: number): DescriptionResponse {
  const currentRevision = revision(`revision-${id}`, label, text, "DRAFT", null);
  return {
    descriptionId: id,
    artworkId: "artwork-1",
    source: "MANUAL",
    displayOrder,
    version: 1,
    currentRevision,
    approvedRevisionId: null,
    revisions: [currentRevision],
    createdAt: "2026-07-21T12:00:00Z",
    updatedAt: "2026-07-21T12:00:00Z",
  };
}

function approvedDescription(value: DescriptionResponse): DescriptionResponse {
  const approved = {
    ...value.currentRevision,
    state: "APPROVED" as const,
    approvedBy: "configured-admin",
    approvedAt: "2026-07-21T13:00:00Z",
  };
  return { ...value, version: 2, currentRevision: approved,
    approvedRevisionId: approved.revisionId, revisions: [approved] };
}

function revision(id: string, label: string, text: string, state: "DRAFT" | "APPROVED",
  parentRevisionId: string | null) {
  return {
    revisionId: id,
    label,
    text,
    state,
    parentRevisionId,
    approvedBy: null,
    approvedAt: null,
    createdAt: "2026-07-21T12:00:00Z",
    updatedAt: "2026-07-21T12:00:00Z",
  };
}
