import { useEffect, useRef, useState } from "react";

import { apiFetch } from "../api/client";
import type { CaptionJobResponse, DescriptionResponse } from "../api/types";

const FIRST_POLL_DELAY = 1000;
const MAX_POLL_DELAY = 5000;

export function CaptionRequestPanel({ artworkId, onGenerated }: {
  artworkId: string;
  onGenerated: (description: DescriptionResponse) => void;
}) {
  const [job, setJob] = useState<CaptionJobResponse | null>(null);
  const [status, setStatus] = useState("");
  const [requesting, setRequesting] = useState(false);
  const timer = useRef<ReturnType<typeof setTimeout> | null>(null);
  const focusTimer = useRef<ReturnType<typeof setTimeout> | null>(null);
  const mounted = useRef(true);

  useEffect(() => () => {
    mounted.current = false;
    if (timer.current) clearTimeout(timer.current);
    if (focusTimer.current) clearTimeout(focusTimer.current);
  }, []);

  function request() {
    if (timer.current) clearTimeout(timer.current);
    setRequesting(true);
    setStatus("Requesting placeholder generation");
    void apiFetch<CaptionJobResponse>(`/api/artworks/${artworkId}/caption-jobs`, { method: "POST" })
      .then((created) => {
        if (!mounted.current) return;
        setJob(created);
        handle(created, FIRST_POLL_DELAY);
      })
      .catch(() => {
        if (mounted.current) setStatus("Placeholder generation could not be requested. Please retry.");
      })
      .finally(() => {
        if (mounted.current) setRequesting(false);
      });
  }

  function handle(current: CaptionJobResponse, nextDelay: number) {
    if (current.state === "FAILED") {
      setStatus(current.errorMessage ?? "Placeholder generation could not be completed. Please retry.");
      return;
    }
    if (current.state === "SUCCEEDED") {
      void addGeneratedDescription(current);
      return;
    }

    setStatus(current.state === "PENDING"
      ? "Placeholder generation pending"
      : "Placeholder generation running");
    timer.current = setTimeout(() => poll(current.jobId, nextDelay), nextDelay);
  }

  function poll(jobId: string, currentDelay: number) {
    void apiFetch<CaptionJobResponse>(`/api/artworks/${artworkId}/caption-jobs/${jobId}`)
      .then((updated) => {
        if (!mounted.current) return;
        setJob(updated);
        handle(updated, Math.min(currentDelay * 2, MAX_POLL_DELAY));
      })
      .catch(() => {
        if (!mounted.current) return;
        setStatus("Caption status could not be checked. Trying again.");
        timer.current = setTimeout(() => poll(jobId, Math.min(currentDelay * 2, MAX_POLL_DELAY)), currentDelay);
      });
  }

  async function addGeneratedDescription(succeeded: CaptionJobResponse) {
    try {
      const descriptions = await apiFetch<DescriptionResponse[]>(`/api/artworks/${artworkId}/descriptions`);
      if (!mounted.current) return;
      const generated = descriptions.find((item) => item.descriptionId === succeeded.resultingDescriptionId);
      if (!generated) throw new Error("Generated description was not returned.");
      onGenerated(generated);
      setStatus("Placeholder draft added");
      focusTimer.current = setTimeout(() => {
        document.getElementById(`description-${generated.descriptionId}-heading`)?.focus();
      }, 0);
    } catch {
      if (mounted.current) setStatus("The generated draft could not be loaded. Refresh the artwork editor.");
    }
  }

  const failed = job?.state === "FAILED";
  const active = job?.state === "PENDING" || job?.state === "RUNNING";

  return (
    <section aria-labelledby="caption-request-heading" className="caption-request-panel">
      <h3 id="caption-request-heading">Placeholder caption generation</h3>
      <p>This creates deterministic demo text, not image analysis, using artwork metadata.</p>
      <button type="button" onClick={request} disabled={requesting || active}>
        {failed ? "Retry placeholder generation" : "Generate placeholder draft"}
      </button>
      {status && <p role="status" aria-live="polite">{status}</p>}
    </section>
  );
}
