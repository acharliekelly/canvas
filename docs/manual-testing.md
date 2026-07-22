# Local MVP manual acceptance

Accessibility is a release criterion. Automated Playwright and axe checks are necessary but do not replace this checklist. Perform it before calling a release complete, using a clean local data set and content you have permission to use.

## Test setup

1. Copy `.env.example` to `.env` and keep the documented local `admin` / `password` pair, or update both the BCrypt hash and E2E plaintext consistently.
2. Run `CONFIRM=1 make reset-local-data`, then `docker compose up --build --wait`.
3. Use Google Chrome at 100% zoom first. Keep DevTools closed while evaluating keyboard focus.
4. Choose one screen reader: NVDA on Windows with Chrome, VoiceOver on macOS with Safari, or Orca on Linux with Chrome. Record its exact version below.
5. Use `frontend/e2e/fixtures/sample-artwork.png`; it is synthetic acceptance data and safe to commit.

Do not use a mouse, trackpad, or touch input during the keyboard pass.

## Keyboard-only workflow

### Sign in and upload

1. Open <http://localhost:5173>. Press Tab through Username, Password, and Sign in; enter the configured credentials and submit with Enter. Expected: focus order follows the page, the controls have visible focus, and the Artworks heading appears.
2. Sign out or use a private window, enter an incorrect password, and submit. Expected: focus moves to the `There is a problem` alert and the error does not expose configuration or internal details.
3. Return to a valid session. Submit Upload artwork with no file. Expected: focus moves to the error summary; its link targets Artwork image; the field is identified as invalid without relying on red alone.
4. Tab to Artwork image and operate the native file chooser with the keyboard. Select the fixture. Enter title `Manual accessibility study`, credit `CANVAS manual test`, and optional context. Submit. Expected: progress is announced, success receives focus, the form clears, and the new artwork link is first in the list.

### Add, reorder, edit, and approve

1. Open the new artwork with Enter. Add `Subjective` text first and `Objective` text second. Expected: each add is announced; each description is a separately named region with visible Draft and Manual status text.
2. Use `Move Objective up`. Expected: ordering changes without pointer input, the saved-order status is announced, Objective is first, and disabled boundary controls remain readable.
3. Change the Objective text and activate Save Objective draft. Expected: the saved status is announced and focus remains in a logical nearby location.
4. Activate Approve Objective. Expected: focus moves into the named confirmation dialog, Tab stays within the modal controls, Escape closes it and returns focus to Approve Objective.
5. Open the dialog again and activate Approve description. Repeat for Subjective. Expected: approval is explicit, each item shows Approved plus approval details, and the approval status is announced.
6. Edit an approved Objective and save it. Expected: it becomes a new Draft, the earlier approved revision remains visible in history, and the UI explains that history is retained. Approve the replacement before continuing.

### Publish, audio, and QR

1. In a new zero-description artwork, confirm Publish artwork is disabled and the page says approval is required. Request placeholder generation. Expected: pending/running/completed changes are announced and the resulting region is visibly identified as Generated and Draft.
2. Edit and save the generated draft. Before approval, confirm it cannot be published. Approve it explicitly.
3. On the manual artwork, activate Publish artwork. Expected: focus enters the `Publish this artwork?` dialog; it names only the approved revisions in display order. Cancel returns focus to Publish artwork.
4. Confirm publication. Expected: publication completion is announced and links appear for the public artwork and QR download.
5. Open the public page. Expected: title and credit are headings/text, descriptions occur in Objective then Subjective reading order, every audio control has a specific label, and each narration has an equivalent visible text transcript.
6. Operate each audio control from the keyboard. Expected: the placeholder WAV starts/stops without trapping focus; the same words remain available as text.
7. Return to the editor and activate Download QR code. Expected: a `.png` download begins without a navigation trap. Re-publish without changes. Expected: the status says the artwork is already up to date and audio/QR assets are reused.

## Focus, screen reader, zoom, and visual checks

### Focus and status

- Tab through every interactive control on sign-in, list, editor, dialogs, public audio, and download paths. Expected: the focus indicator remains clearly visible and no focusable control is skipped or unreachable.
- Trigger sign-in, upload, description, and publication errors. Expected: focus reaches the error summary/dialog, links identify the affected fields, and recovery follows a logical order.
- Open and close both dialogs using buttons and Escape. Expected: initial focus is on the safe confirmation control; focus is contained while open and restored to the invoking button when closed.
- Observe caption generation and publication. Expected: status updates are announced once without moving focus unexpectedly.

### NVDA, VoiceOver, or Orca

With NVDA use Browse/Focus modes and heading/form-field shortcuts. With VoiceOver use the rotor for Headings, Form Controls, and Landmarks. With Orca use structural navigation for headings, form fields, landmarks, and live regions.

- Read sign-in and upload pages. Expected: CANVAS and section headings have correct levels; every input and button has a meaningful name, required state, instructions, and error relationship.
- Trigger invalid sign-in/upload/description states. Expected: the alert and actionable error are spoken; no stack trace, storage key, or administrator internals are announced.
- Read the multi-description editor. Expected: Objective and Subjective regions, Draft/Approved state, Manual/Generated source, uniquely named revision histories, and visual order are announced in DOM order.
- Open each dialog. Expected: dialog role, heading, consequence text, approved-revision list, and buttons are announced; background content is not navigable while modal.
- Request generation and publish. Expected: pending/running/completed status and publication confirmation are spoken.
- Read the public page. Expected: artwork heading/credit, image alternative, description headings in approved order, specifically labeled audio controls, and complete visible transcripts are announced. No draft, approver identity, or admin control is present.

### Zoom and narrow viewport

1. Set browser zoom to 200% at a viewport of at least 1280 CSS pixels. Repeat sign-in, upload, editor, dialogs, public audio, and QR download. Expected: content reflows without clipping, overlap, two-dimensional page scrolling, or loss of operation.
2. Set the viewport to 320 CSS pixels wide at 100% zoom and repeat. Expected: headings, labels, errors, description text, dialogs, audio controls, and buttons remain visible and operable; horizontal scrolling is not required for page content.

### Contrast and non-color cues

- Inspect text, controls, focus outlines, links, errors, disabled controls, and dialog backdrop with a contrast checker. Expected: WCAG AA contrast is met for normal/large text and UI components/focus indicators.
- Ignore color and inspect every workflow state. Expected: Draft, Approved, Manual, Generated, disabled actions, errors, and success are communicated by text, native state, shape/border, or position as well as color.

### Publication privacy and provenance

- Publish an approved revision, then create a new unapproved draft. Refresh the public page and inspect `/public/artworks/{slug}`. Expected: the prior published approved text remains; the draft, approval identity, storage keys, credentials, and internal errors are absent.
- Compare manual and generated cards. Expected: generated text is always identified as Generated and Draft until a human edits/approves it; generation never publishes automatically.

## Results

Do not mark the release complete if any row fails. Link an issue and leave the result `FAIL` until it is retested.

| Date | Browser and OS | Assistive technology | Keyboard workflow | Screen reader | 200% / 320 px | Contrast / non-color | Draft/public privacy | Issues or limitations |
|---|---|---|---|---|---|---|---|---|
| 2026-07-21 | Chrome 150.0.7871.100 / Ubuntu 24.04 | Orca 46.1 | PARTIAL PASS — automated headed keyboard-only sign-in and upload-error path passed; no human visual focus review | LIMITED — Orca started with Speech Dispatcher and logged Chrome AT-SPI events/names, including masked password input; synthesized output was not audible/verifiable by the agent, and the full checklist was not run | NOT RUN | PARTIAL — axe found no violations in tested states; no human contrast inspection | PASS — automated publication journey checked public payload and draft/internal-data exclusion | Release criterion remains open pending a human keyboard, audible screen-reader, zoom/narrow-view, and contrast pass. |
