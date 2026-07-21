import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { apiFetch } from "../api/client";
import { SignInPage } from "./SignInPage";

vi.mock("../api/client", () => ({ apiFetch: vi.fn() }));

const mockedApiFetch = vi.mocked(apiFetch);

describe("SignInPage", () => {
  beforeEach(() => { mockedApiFetch.mockReset(); });

  it("uses semantic credential labels and supports keyboard submission", async () => {
    const user = userEvent.setup();
    const signedIn = vi.fn();
    mockedApiFetch.mockResolvedValueOnce(undefined).mockResolvedValueOnce({
      authenticated: true,
      username: "admin",
      csrfToken: "next-token",
    });
    render(<SignInPage onSignedIn={signedIn} />);

    await user.type(screen.getByLabelText("Username"), "admin");
    await user.type(screen.getByLabelText("Password"), "password{Enter}");

    expect(mockedApiFetch).toHaveBeenNthCalledWith(1, "/api/login", expect.objectContaining({ method: "POST" }));
    expect(await screen.findByRole("status")).toHaveTextContent("Signed in");
    expect(signedIn).toHaveBeenCalledWith(expect.objectContaining({ username: "admin" }));
  });

  it("announces invalid credentials and focuses the error summary", async () => {
    const user = userEvent.setup();
    mockedApiFetch.mockImplementation(() => { throw { status: 401, message: "The username or password was incorrect." }; });
    render(<SignInPage onSignedIn={vi.fn()} />);

    await user.type(screen.getByLabelText("Username"), "admin");
    await user.type(screen.getByLabelText("Password"), "wrong");
    await user.click(screen.getByRole("button", { name: "Sign in" }));

    const summary = await screen.findByRole("alert");
    expect(summary).toHaveTextContent("The username or password was incorrect.");
    expect(summary).toHaveFocus();
  });
});
