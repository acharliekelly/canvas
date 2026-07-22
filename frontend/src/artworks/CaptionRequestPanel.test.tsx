import { act, fireEvent, render, screen } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { apiFetch } from "../api/client";
import type { CaptionJobResponse, DescriptionResponse } from "../api/types";
import { CaptionRequestPanel } from "./CaptionRequestPanel";

vi.mock("../api/client", () => ({ apiFetch: vi.fn() }));
const mockedApiFetch = vi.mocked(apiFetch);

describe("CaptionRequestPanel", () => {
  beforeEach(() => {
    vi.useFakeTimers();
    mockedApiFetch.mockReset();
  });
  afterEach(() => vi.useRealTimers());

  it("explains and announces pending work, then polls only while non-terminal", async () => {
    mockedApiFetch
      .mockResolvedValueOnce(job("PENDING"))
      .mockResolvedValueOnce(job("RUNNING"))
      .mockResolvedValueOnce(job("FAILED"));
    render(<CaptionRequestPanel artworkId="artwork-1" onGenerated={vi.fn()} />);

    expect(screen.getByText(/deterministic demo text, not image analysis/i)).toBeVisible();
    fireEvent.click(screen.getByRole("button", { name: "Generate placeholder draft" }));
    await act(() => Promise.resolve());
    expect(screen.getByRole("status")).toHaveTextContent("Placeholder generation pending");

    await act(() => vi.advanceTimersByTimeAsync(1000));
    expect(screen.getByRole("status")).toHaveTextContent("Placeholder generation running");
    await act(() => vi.advanceTimersByTimeAsync(2000));
    expect(screen.getByRole("status")).toHaveTextContent("could not be completed");

    await act(() => vi.advanceTimersByTimeAsync(10000));
    expect(mockedApiFetch).toHaveBeenCalledTimes(3);
    expect(screen.getByRole("button", { name: "Retry placeholder generation" })).toBeVisible();
  });

  it("adds the generated description and focuses its heading on success", async () => {
    const generated = description();
    const onGenerated = vi.fn((value: DescriptionResponse) => {
      const heading = document.createElement("h3");
      heading.id = `description-${value.descriptionId}-heading`;
      heading.tabIndex = -1;
      document.body.append(heading);
    });
    mockedApiFetch
      .mockResolvedValueOnce(job("PENDING"))
      .mockResolvedValueOnce({ ...job("SUCCEEDED"), resultingDescriptionId: generated.descriptionId })
      .mockResolvedValueOnce([generated]);
    render(<CaptionRequestPanel artworkId="artwork-1" onGenerated={onGenerated} />);

    fireEvent.click(screen.getByRole("button", { name: "Generate placeholder draft" }));
    await act(() => Promise.resolve());
    await act(() => vi.advanceTimersByTimeAsync(1000));
    await act(() => vi.runOnlyPendingTimersAsync());

    expect(onGenerated).toHaveBeenCalledWith(generated);
    expect(screen.getByRole("status")).toHaveTextContent("Placeholder draft added");
    expect(document.getElementById("description-generated-heading")).toHaveFocus();
  });

  it("retries through the same idempotent request endpoint", async () => {
    mockedApiFetch
      .mockResolvedValueOnce(job("FAILED"))
      .mockResolvedValueOnce({ ...job("PENDING"), jobId: "job-2", attemptCount: 2 });
    render(<CaptionRequestPanel artworkId="artwork-1" onGenerated={vi.fn()} />);

    fireEvent.click(screen.getByRole("button", { name: "Generate placeholder draft" }));
    await act(() => Promise.resolve());
    fireEvent.click(screen.getByRole("button", { name: "Retry placeholder generation" }));
    await act(() => Promise.resolve());

    expect(mockedApiFetch).toHaveBeenNthCalledWith(2, "/api/artworks/artwork-1/caption-jobs", { method: "POST" });
    expect(screen.getByRole("status")).toHaveTextContent("Placeholder generation pending");
  });

  it("stops polling when unmounted", async () => {
    mockedApiFetch.mockResolvedValue(job("PENDING"));
    const view = render(<CaptionRequestPanel artworkId="artwork-1" onGenerated={vi.fn()} />);

    fireEvent.click(screen.getByRole("button", { name: "Generate placeholder draft" }));
    await act(() => Promise.resolve());
    view.unmount();
    await act(() => vi.advanceTimersByTimeAsync(10000));

    expect(mockedApiFetch).toHaveBeenCalledTimes(1);
  });
});

function job(state: CaptionJobResponse["state"]): CaptionJobResponse {
  return {
    jobId: "job-1", artworkId: "artwork-1", state, attemptCount: 1,
    errorMessage: state === "FAILED" ? "Placeholder generation could not be completed. Please retry." : null,
    resultingDescriptionId: null, version: 0, createdAt: "2026-07-21T12:00:00Z",
    startedAt: state === "RUNNING" ? "2026-07-21T12:00:01Z" : null,
    completedAt: ["SUCCEEDED", "FAILED"].includes(state) ? "2026-07-21T12:00:02Z" : null,
    updatedAt: "2026-07-21T12:00:02Z",
  };
}

function description(): DescriptionResponse {
  const revision = {
    revisionId: "revision-generated", label: "Placeholder draft", text: "Metadata-only demo text.",
    state: "DRAFT" as const, parentRevisionId: null, approvedBy: null, approvedAt: null,
    createdAt: "2026-07-21T12:00:02Z", updatedAt: "2026-07-21T12:00:02Z",
  };
  return {
    descriptionId: "generated", artworkId: "artwork-1", source: "GENERATED", displayOrder: 1,
    version: 1, currentRevision: revision, approvedRevisionId: null, revisions: [revision],
    createdAt: "2026-07-21T12:00:02Z", updatedAt: "2026-07-21T12:00:02Z",
  };
}
