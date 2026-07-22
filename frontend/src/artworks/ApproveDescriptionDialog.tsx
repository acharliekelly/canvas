import { useEffect, useRef } from "react";

export function ApproveDescriptionDialog({ label, approving, onApprove, onCancel, onReturnFocus }: {
  label: string;
  approving: boolean;
  onApprove: () => void;
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

  const headingId = `approve-${safeId(label)}-heading`;
  return (
    <dialog ref={dialogRef} aria-labelledby={headingId} className="approval-dialog"
      onCancel={(event) => { event.preventDefault(); onCancel(); }}>
      <h2 id={headingId}>Approve {label} description</h2>
      <p>This records the current text as an approved revision. Later edits create a new draft, and this approved revision remains in history.</p>
      <div className="button-row">
        <button type="button" onClick={onCancel} disabled={approving}>Cancel</button>
        <button type="button" onClick={onApprove} disabled={approving} ref={confirmRef}>
          {approving ? "Approving description" : "Approve description"}
        </button>
      </div>
    </dialog>
  );
}

function safeId(value: string) {
  return value.toLowerCase().replace(/[^a-z0-9]+/g, "-");
}
