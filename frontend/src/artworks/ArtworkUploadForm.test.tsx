import { fireEvent, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { apiFetch } from "../api/client";
import { ArtworkUploadForm } from "./ArtworkUploadForm";

vi.mock("../api/client", () => ({ apiFetch: vi.fn() }));

const mockedApiFetch = vi.mocked(apiFetch);
const validPng = new File([new Uint8Array([137, 80, 78, 71])], "study.png", { type: "image/png" });

describe("ArtworkUploadForm", () => {
  beforeEach(() => { mockedApiFetch.mockReset(); });

  it("links an image-required error summary to the image field", async () => {
    const user = userEvent.setup();
    render(<ArtworkUploadForm onUploaded={vi.fn()} />);

    await user.type(screen.getByLabelText("Title"), "Blue Study");
    await user.type(screen.getByLabelText("Artist or display credit"), "A. Artist");
    await user.click(screen.getByRole("button", { name: "Upload artwork" }));

    const error = await screen.findByRole("alert");
    expect(error).toHaveTextContent("Choose an artwork image");
    expect(screen.getByRole("link", { name: "Choose an artwork image" })).toHaveAttribute("href", "#artwork-image");
    expect(mockedApiFetch).not.toHaveBeenCalled();
  });

  it("announces progress and moves focus to the completed status", async () => {
    const user = userEvent.setup();
    let resolveUpload!: (value: unknown) => void;
    mockedApiFetch.mockImplementation(() => new Promise((resolve) => { resolveUpload = resolve; }) as never);
    const uploaded = vi.fn();
    render(<ArtworkUploadForm onUploaded={uploaded} />);

    await user.upload(screen.getByLabelText("Artwork image"), validPng);
    await user.type(screen.getByLabelText("Title"), "Blue Study");
    await user.type(screen.getByLabelText("Artist or display credit"), "A. Artist");
    fireEvent.click(screen.getByRole("button", { name: "Upload artwork" }));
    expect(await screen.findByText("Uploading Blue Study")).toBeVisible();

    resolveUpload({ id: "1", title: "Blue Study", credit: "A. Artist", status: "UPLOADED", createdAt: "now" });
    const status = await screen.findByText("Blue Study uploaded");
    expect(status).toHaveFocus();
    expect(uploaded).toHaveBeenCalled();
  });

  it("shows server validation messages in a focused error summary", async () => {
    const user = userEvent.setup();
    mockedApiFetch.mockImplementation(() => { throw { status: 400, message: "Only PNG and JPEG images are supported." }; });
    render(<ArtworkUploadForm onUploaded={vi.fn()} />);

    await user.upload(screen.getByLabelText("Artwork image"), validPng);
    await user.type(screen.getByLabelText("Title"), "Blue Study");
    await user.type(screen.getByLabelText("Artist or display credit"), "A. Artist");
    await user.click(screen.getByRole("button", { name: "Upload artwork" }));

    const error = await screen.findByRole("alert");
    expect(error).toHaveTextContent("Only PNG and JPEG images are supported.");
    expect(error).toHaveFocus();
  });
});
