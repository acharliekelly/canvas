import { useCallback, useEffect, useRef, useState } from "react";
import type { FormEvent } from "react";

import { apiFetch } from "../api/client";
import type { DescriptionResponse } from "../api/types";
import { ApproveDescriptionDialog } from "./ApproveDescriptionDialog";
import { CaptionRequestPanel } from "./CaptionRequestPanel";
import { PublishPanel } from "./PublishPanel";

interface DraftFields {
  label: string;
  text: string;
}

export function DescriptionEditor({ artworkId, artworkVersion: initialArtworkVersion,
  initialDescriptions, artworkTitle = "this artwork" }: {
  artworkId: string;
  artworkVersion: number;
  initialDescriptions: DescriptionResponse[];
  artworkTitle?: string;
}) {
  const [descriptions, setDescriptions] = useState(() => ordered(initialDescriptions));
  const [artworkVersion, setArtworkVersion] = useState(initialArtworkVersion);
  const [edits, setEdits] = useState<Record<string, DraftFields>>(() => fieldsFor(initialDescriptions));
  const [newLabel, setNewLabel] = useState("");
  const [newText, setNewText] = useState("");
  const [error, setError] = useState<{ message: string; fieldId?: string } | null>(null);
  const [status, setStatus] = useState("");
  const [busyId, setBusyId] = useState<string | null>(null);
  const [approvalId, setApprovalId] = useState<string | null>(null);
  const knownDescriptionIds = useRef(new Set(initialDescriptions.map((item) => item.descriptionId)));
  const errorRef = useRef<HTMLDivElement>(null);
  const statusRef = useRef<HTMLParagraphElement>(null);
  const approveButtons = useRef<Record<string, HTMLButtonElement | null>>({});

  const approval = descriptions.find((item) => item.descriptionId === approvalId) ?? null;
  useEffect(() => {
    if (error && approvalId === null) errorRef.current?.focus();
  }, [approvalId, error]);

  const restoreApprovalFocus = useCallback(() => {
    const approvalButton = approvalId ? approveButtons.current[approvalId] : null;
    if (approvalButton) approvalButton.focus();
    else statusRef.current?.focus();
  }, [approvalId]);

  function reportError(message: string, fieldId?: string) {
    setError({ message, fieldId });
  }

  function add(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const label = newLabel.trim();
    const text = newText.trim();
    if (!label) { reportError("Enter a description label", "new-description-label"); return; }
    if (!text) { reportError("Enter description text", "new-description-text"); return; }

    setError(null);
    setBusyId("new");
    void apiFetch<DescriptionResponse>(`/api/artworks/${artworkId}/descriptions`, jsonRequest("POST", { label, text }))
      .then((created) => {
        knownDescriptionIds.current.add(created.descriptionId);
        setDescriptions((current) => ordered([...current, created]));
        setEdits((current) => ({ ...current, [created.descriptionId]: revisionFields(created) }));
        setArtworkVersion((current) => current + 1);
        setNewLabel("");
        setNewText("");
        setStatus(`${created.currentRevision.label} description added`);
      })
      .catch((caught) => {
        const details = descriptionError(caught, "The description could not be added.", "new-description");
        reportError(details.message, details.fieldId);
      })
      .finally(() => setBusyId(null));
  }

  function change(descriptionId: string, field: keyof DraftFields, value: string) {
    setEdits((current) => ({
      ...current,
      [descriptionId]: { ...current[descriptionId], [field]: value },
    }));
  }

  function save(description: DescriptionResponse) {
    const draft = edits[description.descriptionId];
    const label = draft.label.trim();
    const text = draft.text.trim();
    if (!label) {
      reportError("Enter a description label", `description-${description.descriptionId}-label`);
      return;
    }
    if (!text) {
      reportError("Enter description text", `description-${description.descriptionId}-text`);
      return;
    }

    setError(null);
    setBusyId(description.descriptionId);
    void apiFetch<DescriptionResponse>(
      `/api/artworks/${artworkId}/descriptions/${description.descriptionId}/draft`,
      jsonRequest("PUT", { label, text, version: description.version }),
    ).then((saved) => {
      replaceDescription(saved);
      setStatus(`${saved.currentRevision.label} draft saved`);
    }).catch((caught) => {
      const details = descriptionError(caught, "The draft could not be saved.",
        `description-${description.descriptionId}`);
      reportError(details.message, details.fieldId);
    })
      .finally(() => setBusyId(null));
  }

  function move(index: number, changeBy: -1 | 1) {
    const next = [...descriptions];
    const target = index + changeBy;
    [next[index], next[target]] = [next[target], next[index]];
    const movingId = descriptions[index].descriptionId;
    setBusyId(movingId);
    setError(null);
    void apiFetch<DescriptionResponse[]>(`/api/artworks/${artworkId}/description-order`, jsonRequest("PUT", {
      descriptionIds: next.map((item) => item.descriptionId),
      version: artworkVersion,
    })).then((savedOrder) => {
      setDescriptions(ordered(savedOrder));
      setArtworkVersion((current) => current + 1);
      setStatus("Description order saved");
    }).catch((caught) => reportError(errorMessage(caught, "The description order could not be saved.")))
      .finally(() => setBusyId(null));
  }

  function approve(description: DescriptionResponse) {
    setBusyId(description.descriptionId);
    setError(null);
    void apiFetch<DescriptionResponse>(
      `/api/artworks/${artworkId}/descriptions/${description.descriptionId}/approve`,
      jsonRequest("POST", { version: description.version }),
    ).then((approved) => {
      replaceDescription(approved);
      setArtworkVersion((current) => current + 1);
      setApprovalId(null);
      setStatus(`${approved.currentRevision.label} description approved`);
    }).catch((caught) => {
      setApprovalId(null);
      reportError(errorMessage(caught, "The description could not be approved."));
    })
      .finally(() => setBusyId(null));
  }

  function replaceDescription(updated: DescriptionResponse) {
    setDescriptions((current) => current.map((item) =>
      item.descriptionId === updated.descriptionId ? updated : item));
    setEdits((current) => ({ ...current, [updated.descriptionId]: revisionFields(updated) }));
  }

  function addGeneratedDescription(generated: DescriptionResponse) {
    const alreadyPresent = knownDescriptionIds.current.has(generated.descriptionId);
    knownDescriptionIds.current.add(generated.descriptionId);
    setDescriptions((current) => ordered([
      ...current.filter((item) => item.descriptionId !== generated.descriptionId),
      generated,
    ]));
    setEdits((current) => ({ ...current, [generated.descriptionId]: revisionFields(generated) }));
    if (!alreadyPresent) setArtworkVersion((current) => current + 1);
  }

  return (
    <section aria-labelledby="descriptions-heading" className="description-editor">
      <h2 id="descriptions-heading">Descriptions</h2>
      <p>Add one or more descriptions using your organization&apos;s preferred labels.</p>
      <PublishPanel artworkId={artworkId} artworkTitle={artworkTitle} artworkVersion={artworkVersion}
        descriptions={descriptions}
        onPublished={setArtworkVersion} />
      <CaptionRequestPanel artworkId={artworkId} onGenerated={addGeneratedDescription} />
      {error && <div id="description-error-summary" className="error-summary" role="alert" tabIndex={-1} ref={errorRef}>
        <h3>There is a problem</h3>
        {error.fieldId ? <a href={`#${error.fieldId}`}>{error.message}</a> : <p>{error.message}</p>}
      </div>}

      <form onSubmit={add} noValidate className="new-description-form">
        <h3>Add a description</h3>
        <div className="field">
          <label htmlFor="new-description-label">New description label</label>
          <input id="new-description-label" value={newLabel} onChange={(event) => setNewLabel(event.target.value)}
            aria-required="true" aria-invalid={error?.fieldId === "new-description-label"}
            aria-describedby={error?.fieldId === "new-description-label" ? "description-error-summary" : undefined} />
        </div>
        <div className="field">
          <label htmlFor="new-description-text">New description text</label>
          <textarea id="new-description-text" value={newText} onChange={(event) => setNewText(event.target.value)}
            aria-required="true" aria-invalid={error?.fieldId === "new-description-text"}
            aria-describedby={error?.fieldId === "new-description-text" ? "description-error-summary" : undefined} />
        </div>
        <button type="submit" disabled={busyId !== null}>Add description</button>
      </form>

      {descriptions.length === 0 && <p>No descriptions yet.</p>}
      <div className="description-list">
        {descriptions.map((description, index) => {
          const draft = edits[description.descriptionId] ?? revisionFields(description);
          const current = description.currentRevision;
          const isApproved = current.state === "APPROVED";
          const hasApprovedHistory = current.state === "DRAFT" && description.approvedRevisionId !== null;
          const labelForNames = current.label;
          const isDirty = draft.label !== current.label || draft.text !== current.text;
          const approvalInstructionId = `description-${description.descriptionId}-approval-instruction`;
          return (
            <section key={description.descriptionId} aria-label={`${labelForNames} description`}
              className="description-card">
              <div className="description-card-heading">
                <h3 id={`description-${description.descriptionId}-heading`} tabIndex={-1}>{labelForNames}</h3>
                <p><span className="status-label">Status:</span> {isApproved ? "Approved" : "Draft"}</p>
                <p><span className="status-label">Source:</span> {sentenceCase(description.source)}</p>
              </div>
              {isApproved && <p>{approvalText(current.approvedBy, current.approvedAt)}</p>}
              {hasApprovedHistory && <p className="draft-notice">New draft based on an approved revision. Earlier approved text remains in history.</p>}
              <div className="field">
                <label htmlFor={`description-${description.descriptionId}-label`}>{labelForNames} label</label>
                <input id={`description-${description.descriptionId}-label`} value={draft.label}
                  onChange={(event) => change(description.descriptionId, "label", event.target.value)}
                  aria-invalid={error?.fieldId === `description-${description.descriptionId}-label`}
                  aria-describedby={error?.fieldId === `description-${description.descriptionId}-label`
                    ? "description-error-summary" : undefined} />
              </div>
              <div className="field">
                <label htmlFor={`description-${description.descriptionId}-text`}>
                  {labelForNames} description text
                </label>
                <textarea id={`description-${description.descriptionId}-text`} value={draft.text}
                  onChange={(event) => change(description.descriptionId, "text", event.target.value)}
                  aria-invalid={error?.fieldId === `description-${description.descriptionId}-text`}
                  aria-describedby={error?.fieldId === `description-${description.descriptionId}-text`
                    ? "description-error-summary" : undefined} />
              </div>
              <div className="button-row">
                <button type="button" aria-label={`Move ${labelForNames} up`}
                  disabled={index === 0 || busyId !== null} onClick={() => move(index, -1)}>Move up</button>
                <button type="button" aria-label={`Move ${labelForNames} down`}
                  disabled={index === descriptions.length - 1 || busyId !== null}
                  onClick={() => move(index, 1)}>Move down</button>
                <button type="button" disabled={busyId !== null}
                  aria-label={`Save ${labelForNames} draft`} onClick={() => save(description)}>Save draft</button>
                {!isApproved && <button type="button" disabled={busyId !== null || isDirty}
                  aria-label={`Approve ${labelForNames}`}
                  aria-describedby={isDirty ? approvalInstructionId : undefined}
                  ref={(element) => { approveButtons.current[description.descriptionId] = element; }}
                  onClick={() => setApprovalId(description.descriptionId)}>Approve</button>}
              </div>
              {!isApproved && isDirty && <p id={approvalInstructionId}>Save this draft before approving it.</p>}
              <RevisionHistory description={description} />
            </section>
          );
        })}
      </div>
      <p role="status" aria-live="polite" tabIndex={-1} ref={statusRef}>{status}</p>
      {approval && <ApproveDescriptionDialog label={approval.currentRevision.label}
        approving={busyId === approval.descriptionId} onApprove={() => approve(approval)}
        onCancel={() => setApprovalId(null)} onReturnFocus={restoreApprovalFocus} />}
    </section>
  );
}

function RevisionHistory({ description }: { description: DescriptionResponse }) {
  return (
    <section aria-labelledby={`description-${description.descriptionId}-history`} className="revision-history">
      <h4 id={`description-${description.descriptionId}-history`}>Revision history</h4>
      <ol>
        {description.revisions.map((revision) => <li key={revision.revisionId}>
          <p><span className="status-label">{revision.state === "APPROVED" ? "Approved revision" : "Draft revision"}:</span> {revision.label}</p>
          <p>{revision.text}</p>
          {revision.state === "APPROVED" && <p>{approvalText(revision.approvedBy, revision.approvedAt)}</p>}
        </li>)}
      </ol>
    </section>
  );
}

function ordered(values: DescriptionResponse[]) {
  return [...values].sort((left, right) => left.displayOrder - right.displayOrder);
}

function fieldsFor(values: DescriptionResponse[]) {
  return Object.fromEntries(values.map((value) => [value.descriptionId, revisionFields(value)]));
}

function revisionFields(value: DescriptionResponse): DraftFields {
  return { label: value.currentRevision.label, text: value.currentRevision.text };
}

function jsonRequest(method: "POST" | "PUT", body: unknown): RequestInit {
  return { method, headers: { "Content-Type": "application/json" }, body: JSON.stringify(body) };
}

function sentenceCase(value: string) {
  return value.charAt(0) + value.slice(1).toLowerCase();
}

function approvalText(approvedBy: string | null, approvedAt: string | null) {
  if (!approvedBy || !approvedAt) return "Approval details unavailable";
  return `Approved by ${approvedBy} on ${new Date(approvedAt).toLocaleString()}`;
}

function errorMessage(caught: unknown, fallback: string) {
  return caught instanceof Error ? caught.message : fallback;
}

function descriptionError(caught: unknown, fallback: string, fieldPrefix: string) {
  const message = errorMessage(caught, fallback);
  if (typeof caught !== "object" || caught === null || !("field" in caught)
      || (caught.field !== "label" && caught.field !== "text")) {
    return { message };
  }
  return { message, fieldId: `${fieldPrefix}-${caught.field}` };
}
