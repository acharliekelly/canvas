import { useEffect, useRef, useState } from "react";
import type { FormEvent } from "react";

import { apiFetch } from "../api/client";
import type { ArtworkDetail, ArtworkSummary } from "../api/types";

export function ArtworkUploadForm({ onUploaded }: { onUploaded: (artwork: ArtworkSummary) => void }) {
  const [error, setError] = useState<{ message: string; field?: string } | null>(null);
  const [status, setStatus] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const errorRef = useRef<HTMLDivElement>(null);
  const statusRef = useRef<HTMLParagraphElement>(null);

  useEffect(() => { if (error) errorRef.current?.focus(); }, [error]);
  useEffect(() => { if (status.endsWith(" uploaded")) statusRef.current?.focus(); }, [status]);

  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const form = event.currentTarget;
    const data = new FormData(form);
    const imageInput = form.elements.namedItem("image") as HTMLInputElement;
    const image = imageInput.files?.[0];
    if (!image || image.size === 0) {
      setError({ message: "Choose an artwork image", field: "artwork-image" });
      return;
    }
    data.set("image", image);

    const title = String(data.get("title") ?? "").trim();
    const credit = String(data.get("credit") ?? "").trim();
    if (!title) { setError({ message: "Enter a title", field: "artwork-title" }); return; }
    if (!credit) { setError({ message: "Enter an artist or display credit", field: "artwork-credit" }); return; }

    setError(null);
    setSubmitting(true);
    setStatus(`Uploading ${title}`);
    void upload(form, data);
  }

  async function upload(form: HTMLFormElement, data: FormData) {
    try {
      const artwork = await apiFetch<ArtworkDetail>("/api/artworks", { method: "POST", body: data });
      onUploaded(artwork);
      form.reset();
      setStatus(`${artwork.title} uploaded`);
    } catch (caught) {
      setStatus("");
      setError({ message: errorMessage(caught, "The artwork could not be uploaded.") });
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <section aria-labelledby="upload-heading">
      <h2 id="upload-heading">Upload artwork</h2>
      {error && <div className="error-summary" role="alert" tabIndex={-1} ref={errorRef}><h3>There is a problem</h3>{error.field ? <a href={`#${error.field}`}>{error.message}</a> : <p>{error.message}</p>}</div>}
      <form onSubmit={submit} noValidate>
        <div className="field"><label htmlFor="artwork-image">Artwork image</label><input id="artwork-image" name="image" type="file" accept="image/png,image/jpeg" aria-required="true" /></div>
        <div className="field"><label htmlFor="artwork-title">Title</label><input id="artwork-title" name="title" aria-required="true" /></div>
        <div className="field"><label htmlFor="artwork-credit">Artist or display credit</label><input id="artwork-credit" name="credit" aria-required="true" /></div>
        <div className="field"><label htmlFor="artwork-context">Editorial context <span>(optional)</span></label><textarea id="artwork-context" name="context" /></div>
        <button type="submit" disabled={submitting}>Upload artwork</button>
      </form>
      <p role="status" aria-live="polite" tabIndex={-1} ref={statusRef}>{status}</p>
    </section>
  );
}

function errorMessage(caught: unknown, fallback: string) {
  return typeof caught === "object" && caught !== null && "message" in caught && typeof caught.message === "string"
    ? caught.message
    : fallback;
}
