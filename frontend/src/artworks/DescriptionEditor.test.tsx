import { fireEvent, render, screen, waitFor, within } from "@testing-library/react";
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

  it("uses the artwork version advanced by creation for the next reorder", async () => {
    const user = userEvent.setup();
    const objective = description("objective", "Objective", "A blue square.", 0);
    const subjective = description("subjective", "Subjective", "It feels expansive.", 1);
    mockedApiFetch
      .mockResolvedValueOnce(subjective)
      .mockResolvedValueOnce([{ ...subjective, displayOrder: 0 }, { ...objective, displayOrder: 1 }]);
    render(<DescriptionEditor artworkId="artwork-1" artworkVersion={4} initialDescriptions={[objective]} />);

    await addDescription(user, "Subjective", "It feels expansive.");
    await user.click(screen.getByRole("button", { name: "Move Objective down" }));

    expect(mockedApiFetch).toHaveBeenNthCalledWith(2, "/api/artworks/artwork-1/description-order",
      expect.objectContaining({ body: JSON.stringify({
        descriptionIds: ["subjective", "objective"], version: 5,
      }) }));
  });

  it("does not advance the artwork version when an idempotent caption result already exists", async () => {
    const user = userEvent.setup();
    const objective = description("objective", "Objective", "A blue square.", 0);
    const generated = {
      ...description("generated", "Placeholder draft", "Metadata-only demo text.", 1),
      source: "GENERATED" as const,
    };
    mockedApiFetch
      .mockResolvedValueOnce({
        jobId: "job-1", artworkId: "artwork-1", state: "SUCCEEDED", attemptCount: 1,
        errorMessage: null, resultingDescriptionId: "generated", version: 2,
        createdAt: "2026-07-21T12:00:00Z", startedAt: "2026-07-21T12:00:01Z",
        completedAt: "2026-07-21T12:00:02Z", updatedAt: "2026-07-21T12:00:02Z",
      })
      .mockResolvedValueOnce([objective, generated])
      .mockResolvedValueOnce([{ ...generated, displayOrder: 0 }, { ...objective, displayOrder: 1 }]);
    render(<DescriptionEditor artworkId="artwork-1" artworkVersion={4}
      initialDescriptions={[objective, generated]} />);

    await user.click(screen.getByRole("button", { name: "Generate placeholder draft" }));
    expect(await screen.findByText("Placeholder draft added")).toBeVisible();
    await user.click(screen.getByRole("button", { name: "Move Objective down" }));

    expect(mockedApiFetch).toHaveBeenNthCalledWith(3, "/api/artworks/artwork-1/description-order",
      expect.objectContaining({ body: JSON.stringify({
        descriptionIds: ["generated", "objective"], version: 4,
      }) }));
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

  it.each([
    ["label", "Objective label", "Interpretive"],
    ["text", "Objective description text", "Unsaved replacement text."],
  ])("requires the changed %s to be saved before approval", async (_field, accessibleName, replacement) => {
    const user = userEvent.setup();
    const objective = description("objective", "Objective", "A blue square.", 0);
    render(<DescriptionEditor artworkId="artwork-1" artworkVersion={0} initialDescriptions={[objective]} />);

    const field = screen.getByLabelText(accessibleName);
    await user.clear(field);
    await user.type(field, replacement);

    const approve = screen.getByRole("button", { name: "Approve Objective" });
    expect(approve).toBeDisabled();
    expect(approve).toHaveAccessibleDescription("Save this draft before approving it.");
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
    expect(mockedApiFetch).not.toHaveBeenCalled();
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
    expect(within(dialog).getByRole("button", { name: "Approve description" })).toHaveFocus();
    expect(mockedApiFetch).not.toHaveBeenCalled();
    await user.click(within(dialog).getByRole("button", { name: "Approve description" }));

    expect((await screen.findAllByText(/Approved by configured-admin/))[0]).toBeVisible();
    expect(mockedApiFetch).toHaveBeenCalledWith(
      "/api/artworks/artwork-1/descriptions/objective/approve",
      expect.objectContaining({ method: "POST", body: JSON.stringify({ version: 1 }) }),
    );
  });

  it("publishes with the artwork version advanced by approval", async () => {
    const user = userEvent.setup();
    const objective = description("objective", "Objective", "A blue square.", 0);
    mockedApiFetch
      .mockResolvedValueOnce(approvedDescription(objective))
      .mockResolvedValueOnce({
        publicationId: "publication-1",
        slug: "blue-study-123",
        publishedAt: "2026-07-21T12:00:00Z",
        artworkVersion: 6,
        created: true,
        descriptions: [{ label: "Objective", text: "A blue square." }],
      });
    render(<DescriptionEditor artworkId="artwork-1" artworkVersion={4} initialDescriptions={[objective]} />);

    await user.click(screen.getByRole("button", { name: "Approve Objective" }));
    await user.click(screen.getByRole("button", { name: "Approve description" }));
    expect((await screen.findAllByText(/Approved by configured-admin/))[0]).toBeVisible();
    await user.click(screen.getByRole("button", { name: "Publish artwork" }));
    await user.click(screen.getByRole("button", { name: "Confirm publication" }));

    expect(mockedApiFetch).toHaveBeenNthCalledWith(2, "/api/artworks/artwork-1/publication",
      expect.objectContaining({ body: JSON.stringify({ version: 5 }) }));
  });

  it("cancels approval with Escape and restores focus to the invoking button", async () => {
    const user = userEvent.setup();
    const objective = description("objective", "Objective", "A blue square.", 0);
    render(<DescriptionEditor artworkId="artwork-1" artworkVersion={0} initialDescriptions={[objective]} />);

    const approve = screen.getByRole("button", { name: "Approve Objective" });
    await user.click(approve);
    expect(screen.getByRole("dialog", { name: "Approve Objective description" })).toBeVisible();

    fireEvent(screen.getByRole("dialog"), new Event("cancel", { cancelable: true }));

    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
    expect(approve).toHaveFocus();
    expect(mockedApiFetch).not.toHaveBeenCalled();
  });

  it("closes the modal and focuses the page error summary when approval fails", async () => {
    const user = userEvent.setup();
    const objective = description("objective", "Objective", "A blue square.", 0);
    mockedApiFetch.mockRejectedValueOnce(apiError(
      "This description changed after it was loaded. Refresh and try again.", "stale_version"));
    render(<DescriptionEditor artworkId="artwork-1" artworkVersion={0} initialDescriptions={[objective]} />);

    await user.click(screen.getByRole("button", { name: "Approve Objective" }));
    await user.click(screen.getByRole("button", { name: "Approve description" }));

    await waitFor(() => expect(screen.queryByRole("dialog", { name: "Approve Objective description" }))
      .not.toBeInTheDocument());
    const summary = screen.getByRole("alert");
    expect(summary).toHaveTextContent("This description changed after it was loaded");
    expect(summary).toHaveFocus();
  });

  it.each([
    ["label", "Objective label"],
    ["text", "Objective description text"],
  ])("links server %s metadata to the affected saved-description field", async (field, accessibleName) => {
    const user = userEvent.setup();
    const objective = description("objective", "Objective", "A blue square.", 0);
    mockedApiFetch.mockRejectedValueOnce(apiError(
      `Description ${field} is invalid.`, "invalid_description", field));
    render(<DescriptionEditor artworkId="artwork-1" artworkVersion={0} initialDescriptions={[objective]} />);

    await user.click(screen.getByRole("button", { name: "Save Objective draft" }));

    const summary = await screen.findByRole("alert");
    expect(within(summary).getByRole("link", { name: `Description ${field} is invalid.` }))
      .toHaveAttribute("href", `#description-objective-${field}`);
    expect(screen.getByLabelText(accessibleName)).toHaveAttribute("aria-invalid", "true");
    expect(screen.getByLabelText(accessibleName))
      .toHaveAttribute("aria-describedby", "description-error-summary");
  });

  it.each([
    ["label", "New description label"],
    ["text", "New description text"],
  ])("links server %s metadata to the affected new-description field", async (field, accessibleName) => {
    const user = userEvent.setup();
    mockedApiFetch.mockRejectedValueOnce(apiError(
      `Description ${field} is invalid.`, "invalid_description", field));
    render(<DescriptionEditor artworkId="artwork-1" artworkVersion={0} initialDescriptions={[]} />);

    await addDescription(user, "Objective", "A blue square.");

    const summary = await screen.findByRole("alert");
    expect(within(summary).getByRole("link", { name: `Description ${field} is invalid.` }))
      .toHaveAttribute("href", `#new-description-${field}`);
    expect(screen.getByLabelText(accessibleName)).toHaveAttribute("aria-invalid", "true");
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
    expect(screen.getAllByText("A blue square.")).toHaveLength(2);
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

function apiError(message: string, code: string, field?: string) {
  return Object.assign(new Error(message), { status: 400, code, field });
}
