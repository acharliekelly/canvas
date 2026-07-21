import { useEffect, useState } from "react";
import type { ReactNode } from "react";

import { apiFetch } from "../api/client";
import type { SessionResponse } from "../api/types";
import { ArtworkListPage } from "../artworks/ArtworkListPage";
import { SignInPage } from "./SignInPage";

export function SessionProvider({ children }: { children?: ReactNode }) {
  const [session, setSession] = useState<SessionResponse | null>(null);
  const [unavailable, setUnavailable] = useState(false);

  useEffect(() => {
    let active = true;
    apiFetch<SessionResponse>("/api/session")
      .then((loaded) => { if (active) setSession(loaded); })
      .catch(() => { if (active) setUnavailable(true); });
    return () => { active = false; };
  }, []);

  if (unavailable) return <p role="alert">The sign-in service is unavailable.</p>;
  if (!session) return <p role="status">Loading session</p>;
  if (!session.authenticated) return <SignInPage onSignedIn={setSession} />;
  return <>{children ?? <ArtworkListPage />}</>;
}
