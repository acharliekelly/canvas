import { fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { axe } from "jest-axe";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { apiFetch } from "../api/client";
import type { DescriptionResponse } from "../api/types";
import { PublishPanel } from "./PublishPanel";

vi.mock("../api/client", () => ({ apiFetch: vi.fn() }));
const mockedApiFetch = vi.mocked(apiFetch);

describe("PublishPanel", () => {
  beforeEach(() => mockedApiFetch.mockReset());

  it("disables publication and explains the approval requirement", async () => {
    const { container } = render(<PublishPanel artworkId="artwork-1" artworkVersion={3}
      descriptions={[draftDescription()]} onPublished={vi.fn()} />);

    expect(screen.getByRole("button", { name: "Publish artwork" })).toBeDisabled();
    expect(screen.getByText("Approve at least one description before publishing.")).toBeVisible();
    expect(await axe(container)).toHaveNoViolations();
  });

  it("lists only approved revisions in display order, including approved history behind a draft", () => {
    const subjective = approvedDescription("subjective", "Subjective", "An expansive arrangement.", 0);
    const objective = approvedDescription("objective", "Objective", "A blue square.", 1);
    const objectiveWithDraft = {
      ...objective,
      currentRevision: revision("objective-draft", "Objective", "Unapproved cobalt draft.", "DRAFT"),
      revisions: [objective.currentRevision,
        revision("objective-draft", "Objective", "Unapproved cobalt draft.", "DRAFT")],
    } satisfies DescriptionResponse;

    render(<PublishPanel artworkId="artwork-1" artworkVersion={3}
      descriptions={[objectiveWithDraft, draftDescription(), subjective]} onPublished={vi.fn()} />);

    const list = screen.getByRole("list", { name: "Approved descriptions to publish" });
    expect(within(list).getAllByRole("listitem").map((item) => item.textContent))
      .toEqual(["Subjective: An expansive arrangement.", "Objective: A blue square."]);
    expect(list).not.toHaveTextContent("Unapproved cobalt draft");
    expect(list).not.toHaveTextContent("Private draft");
  });

  it("requires confirmation before publishing and exposes the success link", async () => {
    const user = userEvent.setup();
    const onPublished = vi.fn();
    mockedApiFetch.mockResolvedValueOnce({
      publicationId: "publication-1", slug: "blue-study-123", publishedAt: "2026-07-21T12:00:00Z",
      artworkVersion: 4, created: true, descriptions: [{ label: "Objective", text: "A blue square." }],
    });
    render(<PublishPanel artworkId="artwork-1" artworkVersion={3}
      descriptions={[approvedDescription("objective", "Objective", "A blue square.", 0)]}
      onPublished={onPublished} />);

    await user.click(screen.getByRole("button", { name: "Publish artwork" }));
    const dialog = screen.getByRole("dialog", { name: "Publish this artwork?" });
    expect(dialog).toHaveTextContent("Only the approved revisions listed here will be public");
    const preview = within(dialog).getByRole("list", { name: "Approved revisions in this publication" });
    expect(within(preview).getAllByRole("listitem").map((item) => item.textContent))
      .toEqual(["Objective: A blue square."]);
    expect(within(dialog).getByRole("button", { name: "Confirm publication" })).toHaveFocus();
    expect(mockedApiFetch).not.toHaveBeenCalled();

    await user.click(within(dialog).getByRole("button", { name: "Confirm publication" }));

    expect(await screen.findByRole("status")).toHaveTextContent("Artwork published");
    expect(screen.getByRole("link", { name: "Open published artwork" }))
      .toHaveAttribute("href", "/artworks/blue-study-123");
    expect(mockedApiFetch).toHaveBeenCalledWith("/api/artworks/artwork-1/publication", {
      method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ version: 3 }),
    });
    expect(onPublished).toHaveBeenCalledWith(4);
    expect(screen.getByRole("button", { name: "Publish artwork" })).toHaveFocus();
  });

  it("does not restore trigger focus during a publishing render", async () => {
    const user = userEvent.setup();
    let completePublication!: (result: {
      publicationId: string;
      slug: string;
      publishedAt: string;
      artworkVersion: number;
      created: boolean;
      descriptions: { label: string; text: string }[];
    }) => void;
    mockedApiFetch.mockReturnValueOnce(new Promise((resolve) => { completePublication = resolve; }));
    render(<PublishPanel artworkId="artwork-1" artworkVersion={3}
      descriptions={[approvedDescription("objective", "Objective", "A blue square.", 0)]}
      onPublished={vi.fn()} />);

    const publish = screen.getByRole("button", { name: "Publish artwork" });
    await user.click(publish);
    const confirm = screen.getByRole("button", { name: "Confirm publication" });
    const triggerFocus = vi.spyOn(publish, "focus");
    await user.click(confirm);

    expect(screen.getByRole("button", { name: "Publishing artwork" })).toHaveFocus();
    expect(triggerFocus).not.toHaveBeenCalled();

    completePublication({
      publicationId: "publication-1", slug: "blue-study-123", publishedAt: "2026-07-21T12:00:00Z",
      artworkVersion: 4, created: true, descriptions: [{ label: "Objective", text: "A blue square." }],
    });
    expect(await screen.findByRole("status")).toHaveTextContent("Artwork published");
    expect(triggerFocus).toHaveBeenCalledTimes(1);
  });

  it("shows an actionable focused server error and restores focus when confirmation is cancelled", async () => {
    const user = userEvent.setup();
    mockedApiFetch.mockRejectedValueOnce(new Error(
      "This artwork changed after it was loaded. Refresh and try again."));
    render(<PublishPanel artworkId="artwork-1" artworkVersion={3}
      descriptions={[approvedDescription("objective", "Objective", "A blue square.", 0)]}
      onPublished={vi.fn()} />);

    const publish = screen.getByRole("button", { name: "Publish artwork" });
    await user.click(publish);
    fireEvent(screen.getByRole("dialog"), new Event("cancel", { cancelable: true }));
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
    expect(publish).toHaveFocus();

    await user.click(publish);
    await user.click(screen.getByRole("button", { name: "Confirm publication" }));
    const alert = await screen.findByRole("alert");
    expect(alert).toHaveTextContent("Refresh and try again");
    await waitFor(() => expect(alert).toHaveFocus());
  });
});

function draftDescription(): DescriptionResponse {
  const currentRevision = revision("draft-1", "Editorial", "Private draft.", "DRAFT");
  return {
    descriptionId: "draft", artworkId: "artwork-1", source: "MANUAL", displayOrder: 2, version: 1,
    currentRevision, approvedRevisionId: null, revisions: [currentRevision],
    createdAt: "2026-07-21T12:00:00Z", updatedAt: "2026-07-21T12:00:00Z",
  };
}

function approvedDescription(id: string, label: string, text: string,
  displayOrder: number): DescriptionResponse {
  const currentRevision = revision(`${id}-approved`, label, text, "APPROVED");
  return {
    descriptionId: id, artworkId: "artwork-1", source: "MANUAL", displayOrder, version: 2,
    currentRevision, approvedRevisionId: currentRevision.revisionId, revisions: [currentRevision],
    createdAt: "2026-07-21T12:00:00Z", updatedAt: "2026-07-21T12:00:00Z",
  };
}

function revision(id: string, label: string, text: string, state: "DRAFT" | "APPROVED") {
  return {
    revisionId: id, label, text, state, parentRevisionId: null,
    approvedBy: state === "APPROVED" ? "admin" : null,
    approvedAt: state === "APPROVED" ? "2026-07-21T12:00:00Z" : null,
    createdAt: "2026-07-21T12:00:00Z", updatedAt: "2026-07-21T12:00:00Z",
  };
}
