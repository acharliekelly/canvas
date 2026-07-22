import { useEffect, useState } from "react";

import { SessionProvider } from "./auth/SessionProvider";
import { PublicArtworkPage } from "./publication/PublicArtworkPage";

type Readiness = "checking" | "ready" | "unavailable";

export default function App() {
  const publicArtworkMatch = window.location.pathname.match(/^\/artworks\/([^/]+)\/?$/);
  if (publicArtworkMatch) return <PublicArtworkPage slug={decodeSlug(publicArtworkMatch[1])} />;
  return <AdminApp />;
}

function decodeSlug(value: string): string | null {
  try {
    const decoded = decodeURIComponent(value);
    return /^[a-z0-9-]+$/.test(decoded) ? decoded : null;
  } catch {
    return null;
  }
}

function AdminApp() {
  const [readiness, setReadiness] = useState<Readiness>("checking");

  useEffect(() => {
    const controller = new AbortController();

    async function checkBackend() {
      try {
        const response = await fetch("/api/health", { signal: controller.signal });
        const body: unknown = await response.json();

        if (response.ok && isReady(body)) {
          setReadiness("ready");
          return;
        }
      } catch (error) {
        if (error instanceof DOMException && error.name === "AbortError") {
          return;
        }
      }

      setReadiness("unavailable");
    }

    void checkBackend();
    return () => controller.abort();
  }, []);

  return (
    <main>
      <h1>CANVAS</h1>
      <p>Captioning and Narration for Visual Accessibility Services</p>
      <p role="status">{readinessMessage(readiness)}</p>
      <SessionProvider />
    </main>
  );
}

function isReady(value: unknown): value is { status: "ready" } {
  return typeof value === "object" && value !== null && (value as { status?: unknown }).status === "ready";
}

function readinessMessage(readiness: Readiness): string {
  switch (readiness) {
    case "ready":
      return "System ready";
    case "unavailable":
      return "Backend unavailable";
    default:
      return "Checking system status";
  }
}
