import { useEffect, useRef, useState } from "react";
import type { FormEvent } from "react";

import { apiFetch } from "../api/client";
import type { ArtworkDetail, ArtworkSummary } from "../api/types";

type UploadField = "image" | "title" | "credit" | "context";

const fieldIds: Record<UploadField, string> = {
  image: "artwork-image",
  title: "artwork-title",
  credit: "artwork-credit",
  context: "artwork-context",
};

export function ArtworkUploadForm({ onUploaded }: { onUploaded: (artwork: ArtworkSummary) => void }) {
  const [error, setError] = useState<{ message: string; field?: UploadField } | null>(null);
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
      setError({ message: "Choose an artwork image", field: "image" });
      return;
    }
    data.set("image", image);

    const title = String(data.get("title") ?? "").trim();
    const credit = String(data.get("credit") ?? "").trim();
    if (!title) { setError({ message: "Enter a title", field: "title" }); return; }
    if (!credit) { setError({ message: "Enter an artist or display credit", field: "credit" }); return; }

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
      setError(errorDetails(caught, "The artwork could not be uploaded."));
    } finally {
      setSubmitting(false);
    }
  }

  const errorFieldId = error?.field ? fieldIds[error.field] : undefined;
  const fieldError = (field: UploadField) => error?.field === field ? error : null;

  return (
    <section aria-labelledby="upload-heading">
      <h2 id="upload-heading">Upload artwork</h2>
      {error && <div className="error-summary" role="alert" tabIndex={-1} ref={errorRef}><h3>There is a problem</h3>{errorFieldId ? <a href={`#${errorFieldId}`}>{error.message}</a> : <p>{error.message}</p>}</div>}
      <form onSubmit={submit} noValidate>
        <div className="field"><label htmlFor="artwork-image">Artwork image</label><input id="artwork-image" name="image" type="file" accept="image/png,image/jpeg" aria-required="true" aria-invalid={Boolean(fieldError("image"))} aria-describedby={fieldError("image") ? "artwork-image-error" : undefined} />{fieldError("image") && <p className="field-error" id="artwork-image-error">{error?.message}</p>}</div>
        <div className="field"><label htmlFor="artwork-title">Title</label><input id="artwork-title" name="title" aria-required="true" aria-invalid={Boolean(fieldError("title"))} aria-describedby={fieldError("title") ? "artwork-title-error" : undefined} />{fieldError("title") && <p className="field-error" id="artwork-title-error">{error?.message}</p>}</div>
        <div className="field"><label htmlFor="artwork-credit">Artist or display credit</label><input id="artwork-credit" name="credit" aria-required="true" aria-invalid={Boolean(fieldError("credit"))} aria-describedby={fieldError("credit") ? "artwork-credit-error" : undefined} />{fieldError("credit") && <p className="field-error" id="artwork-credit-error">{error?.message}</p>}</div>
        <div className="field"><label htmlFor="artwork-context">Editorial context <span>(optional)</span></label><textarea id="artwork-context" name="context" aria-invalid={Boolean(fieldError("context"))} aria-describedby={fieldError("context") ? "artwork-context-error" : undefined} />{fieldError("context") && <p className="field-error" id="artwork-context-error">{error?.message}</p>}</div>
        <button type="submit" disabled={submitting}>Upload artwork</button>
      </form>
      <p role="status" aria-live="polite" tabIndex={-1} ref={statusRef}>{status}</p>
    </section>
  );
}

function errorDetails(caught: unknown, fallback: string): { message: string; field?: UploadField } {
  if (typeof caught !== "object" || caught === null) return { message: fallback };
  const message = "message" in caught && typeof caught.message === "string" ? caught.message : fallback;
  const field = "field" in caught && isUploadField(caught.field) ? caught.field : undefined;
  return { message, field };
}

function isUploadField(value: unknown): value is UploadField {
  return value === "image" || value === "title" || value === "credit" || value === "context";
}
