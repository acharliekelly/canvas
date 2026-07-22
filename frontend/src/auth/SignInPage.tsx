import { useEffect, useRef, useState } from "react";
import type { FormEvent } from "react";

import { apiFetch } from "../api/client";
import type { SessionResponse } from "../api/types";

interface SignInPageProps {
  onSignedIn: (session: SessionResponse) => void;
}

export function SignInPage({ onSignedIn }: SignInPageProps) {
  const [error, setError] = useState<string | null>(null);
  const [status, setStatus] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const errorRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (error) errorRef.current?.focus();
  }, [error]);

  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setError(null);
    setStatus("Signing in");
    setSubmitting(true);
    const data = new FormData(event.currentTarget);
    void signIn(data);
  }

  async function signIn(data: FormData) {
    try {
      await apiFetch<void>("/api/login", {
        method: "POST",
        body: new URLSearchParams({
          username: String(data.get("username") ?? ""),
          password: String(data.get("password") ?? ""),
        }),
      });
      const session = await apiFetch<SessionResponse>("/api/session");
      setStatus("Signed in");
      onSignedIn(session);
    } catch (caught) {
      setStatus("");
      setError(errorMessage(caught, "Sign in failed. Try again."));
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <section aria-labelledby="sign-in-heading">
      <h2 id="sign-in-heading">Administrator sign in</h2>
      {error && <div className="error-summary" role="alert" tabIndex={-1} ref={errorRef}><h3>There is a problem</h3><p>{error}</p></div>}
      <form onSubmit={submit}>
        <div className="field"><label htmlFor="username">Username</label><input id="username" name="username" autoComplete="username" required /></div>
        <div className="field"><label htmlFor="password">Password</label><input id="password" name="password" type="password" autoComplete="current-password" required /></div>
        <button type="submit" disabled={submitting}>Sign in</button>
      </form>
      <p role="status" aria-live="polite">{status}</p>
    </section>
  );
}

function errorMessage(caught: unknown, fallback: string) {
  return typeof caught === "object" && caught !== null && "message" in caught && typeof caught.message === "string"
    ? caught.message
    : fallback;
}
