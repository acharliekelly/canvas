import { useCallback, useEffect, useRef, useState } from "react";

import { apiFetch } from "../api/client";
import type { DescriptionResponse, PublicationResult, PublishedDescription } from "../api/types";

export function PublishPanel({ artworkId, artworkVersion, descriptions, onPublished }: {
  artworkId: string;
  artworkVersion: number;
  descriptions: DescriptionResponse[];
  onPublished: (artworkVersion: number) => void;
}) {
  const approved = approvedDescriptions(descriptions);
  const [confirming, setConfirming] = useState(false);
  const [publishing, setPublishing] = useState(false);
  const [error, setError] = useState("");
  const [status, setStatus] = useState("");
  const [result, setResult] = useState<PublicationResult | null>(null);
  const publishButtonRef = useRef<HTMLButtonElement>(null);
  const errorRef = useRef<HTMLParagraphElement>(null);

  useEffect(() => {
    if (error) errorRef.current?.focus();
  }, [error]);

  const closeConfirmation = useCallback(() => setConfirming(false), []);
  const restorePublishFocus = useCallback(() => publishButtonRef.current?.focus(), []);

  function publish() {
    setPublishing(true);
    setError("");
    setStatus("");
    void apiFetch<PublicationResult>(`/api/artworks/${artworkId}/publication`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ version: artworkVersion }),
    }).then((published) => {
      setResult(published);
      setConfirming(false);
      setStatus(published.created ? "Artwork published" : "Published artwork is already up to date");
      onPublished(published.artworkVersion);
    }).catch((caught) => {
      setConfirming(false);
      setError(caught instanceof Error ? caught.message : "The artwork could not be published. Try again.");
    }).finally(() => setPublishing(false));
  }

  return (
    <section aria-labelledby="publication-heading" className="publish-panel">
      <h2 id="publication-heading">Publication</h2>
      {approved.length === 0 ? <p>Approve at least one description before publishing.</p> : <>
        <p>Only approved revisions are included. Drafts and approval history stay private.</p>
        <ApprovedRevisionList descriptions={approved} label="Approved descriptions to publish" />
      </>}
      {error && <p role="alert" tabIndex={-1} ref={errorRef} className="error-summary">{error}</p>}
      <button type="button" ref={publishButtonRef} disabled={approved.length === 0 || publishing}
        onClick={() => setConfirming(true)}>Publish artwork</button>
      {status && <p role="status" aria-live="polite">{status}</p>}
      {result && <p><a href={`/artworks/${result.slug}`}>Open published artwork</a></p>}
      {confirming && <PublicationDialog publishing={publishing} descriptions={approved} onConfirm={publish}
        onCancel={closeConfirmation} onReturnFocus={restorePublishFocus} />}
    </section>
  );
}

function PublicationDialog({ publishing, descriptions, onConfirm, onCancel, onReturnFocus }: {
  publishing: boolean;
  descriptions: PublishedDescription[];
  onConfirm: () => void;
  onCancel: () => void;
  onReturnFocus: () => void;
}) {
  const dialogRef = useRef<HTMLDialogElement>(null);
  const confirmRef = useRef<HTMLButtonElement>(null);

  useEffect(() => {
    const dialog = dialogRef.current;
    if (dialog && !dialog.open) {
      if (typeof dialog.showModal === "function") dialog.showModal();
      else dialog.setAttribute("open", "");
    }
    confirmRef.current?.focus();
    return onReturnFocus;
  }, [onReturnFocus]);

  return <dialog ref={dialogRef} aria-labelledby="publication-confirm-heading" className="approval-dialog"
    onCancel={(event) => { event.preventDefault(); onCancel(); }}>
    <h2 id="publication-confirm-heading">Publish this artwork?</h2>
    <p>Only the approved revisions listed here will be public. Later drafts remain private until approved and republished.</p>
    <ApprovedRevisionList descriptions={descriptions} label="Approved revisions in this publication" />
    <div className="button-row">
      <button type="button" onClick={onCancel} disabled={publishing}>Cancel</button>
      <button type="button" onClick={onConfirm} disabled={publishing} ref={confirmRef}>
        {publishing ? "Publishing artwork" : "Confirm publication"}
      </button>
    </div>
  </dialog>;
}

function ApprovedRevisionList({ descriptions, label }: {
  descriptions: PublishedDescription[];
  label: string;
}) {
  return <ul aria-label={label} className="publication-description-list">
    {descriptions.map((description, index) => <li key={`${index}-${description.label}`}>
      <strong>{description.label}:</strong> {description.text}
    </li>)}
  </ul>;
}

function approvedDescriptions(descriptions: DescriptionResponse[]): PublishedDescription[] {
  return [...descriptions]
    .sort((left, right) => left.displayOrder - right.displayOrder)
    .flatMap((description) => {
      if (!description.approvedRevisionId) return [];
      const approved = description.revisions.find(
        (revision) => revision.revisionId === description.approvedRevisionId && revision.state === "APPROVED",
      );
      return approved ? [{ label: approved.label, text: approved.text }] : [];
    });
}
