# CANVAS

**Captioning and Narration for Visual Accessibility Services**

CANVAS is an accessibility platform for creating, reviewing, publishing, and sharing rich visual descriptions of artwork. The project is designed to help artists, galleries, museums, and community organizations make visual art more accessible to blind and low-vision audiences.

CANVAS uses machine-generated descriptions as drafts, not final authority. A human reviewer edits and approves each description before publication. Approved descriptions are published as text; the intended production workflow can also turn that exact text into narration and link the public page from a printable QR code placed beside the artwork.

## Project status

CANVAS includes a local, admin-operated MVP that proves the complete workflow:

1. Upload an artwork image.
2. Create a manual draft or request deterministic placeholder text derived only from submitted metadata; the placeholder worker does not inspect the image.
3. Review and edit the draft through revision history.
4. Explicitly approve the exact revision that may be published.
5. Create or reuse a generic placeholder WAV and a QR code for the public URL.
6. Publish an artwork page containing the approved description text.

The MVP is not a production service. It uses a deterministic, model-free placeholder caption worker and a generic placeholder WAV that does not narrate the approved text; no image model, GPU, cloud account, or paid service is required.

The implemented workflow is a locally runnable demonstration, not release acceptance. Human accessibility gates remain pending, including complete keyboard and visible-focus review, screen-reader verification, 200% zoom and 320 CSS-pixel reflow checks, and manual contrast review. See [Manual testing](docs/manual-testing.md) for the required acceptance procedure.

## Why CANVAS

Many artworks are exhibited without meaningful visual descriptions. Existing alternatives may require specialized staff, expensive vendors, or substantial manual effort. CANVAS aims to reduce that production burden while preserving human judgment, artistic context, and editorial responsibility.

The project is being developed with potential nonprofit and accessibility-sector partnerships in mind. Affordability is a product requirement, not an afterthought: the intended operating model should remain plausible for an organization whose available monthly technology budget may be measured in tens of dollars.

## Current architecture

CANVAS is implemented as a modular monorepo:

```text
canvas/
├── frontend/        React administration and public experience
├── backend/         Spring Boot API and workflow orchestration
├── caption-worker/  Deterministic, model-free Python caption contract
├── infrastructure/  Local development and deployment configuration
└── docs/            Product, architecture, cost, and decision records
```

At a high level:

```text
React frontend (administration and public pages)
      |
Spring Boot modular-monolith API
      ├── PostgreSQL application state
      ├── private S3-compatible object storage
      └── asynchronous caption jobs
                    |
          deterministic FastAPI worker
          (submitted metadata only; no model or GPU)
```

The backend owns editorial, persistence, asset, and publication authority. Caption execution is isolated behind a typed HTTP contract, and object storage is isolated behind an S3-compatible interface. A real caption model, GPU runtime, and execution provider remain future evaluations rather than selected components. See [Architecture](docs/architecture.md) and the [accepted decision records](docs/decisions/README.md) for the implemented boundaries.

## Core principles

- **Human-reviewed accessibility:** generated descriptions remain drafts until approved.
- **Accessibility by construction:** keyboard use, screen-reader support, semantic structure, transcripts, and inclusive testing are baseline requirements.
- **Respectful description:** distinguish visible facts from interpretation and avoid unsupported assumptions.
- **Cost-aware architecture:** GPU resources should scale to zero when idle, generated assets should be cached, and recurring costs should remain legible.
- **Vendor portability:** prefer open standards and S3-compatible storage over unnecessary lock-in.
- **Privacy and consent:** artwork ownership, publication status, and access controls must be explicit.
- **Real project discipline:** tests, documentation, reviewable changes, and architectural decision records are part of the product.

## MVP boundaries

The first MVP is admin-only. It does not include public artist registration, automated publishing, billing, marketplace features, or model training. See [Product Scope](docs/product-scope.md) and [Roadmap](docs/roadmap.md).

## Run the local MVP

### Prerequisites

- Docker Engine with Docker Compose v2 and at least 4 GB available memory;
- Java 25, Node.js 24 with npm, and Python 3.13 for host-side verification;
- GNU Make and curl;
- Playwright's bundled Chromium for automated E2E checks. Install it with `cd frontend && npx playwright install chromium`. Google Chrome is optional for manual assistive-technology checks; set `CANVAS_E2E_BROWSER_CHANNEL=chrome` only when deliberately running those checks in Chrome.

Copy the local sample configuration. `.env` is ignored by Git.

```bash
cp .env.example .env
```

The MinIO root credentials in this local-only file are used only by the MinIO service and its one-shot initializer. The backend uses the separate `CANVAS_S3_ACCESS_KEY` and `CANVAS_S3_SECRET_KEY`, scoped to the configured private originals and generated-asset buckets.

Create the ignored worker environment used by host-side verification:

```bash
python3.13 -m venv caption-worker/.venv
caption-worker/.venv/bin/python -m pip install -e 'caption-worker[test]'
```

`Makefile` prefers `caption-worker/.venv/bin/python` when it exists. On Windows, activate the equivalent virtual environment before running `make verify`.

The sample administrator is `admin` with local-only password `password`. To replace it, generate a BCrypt hash and copy the text after the first colon into `CANVAS_ADMIN_PASSWORD_HASH`, retaining the single quotes around the hash:

```bash
docker run --rm httpd:2.4-alpine htpasswd -bnBC 12 admin 'choose-a-local-password'
```

Set `CANVAS_E2E_ADMIN_PASSWORD` to the matching plaintext only if Playwright will use that local account. Never use the sample credentials or commit `.env` in a deployed environment.

Start the services and wait for all health checks:

```bash
docker compose up --build --wait
```

Open or query:

- administration and public pages: <http://localhost:5173>
- backend readiness: <http://localhost:8080/api/health>
- placeholder caption worker readiness: <http://localhost:8000/health>
- MinIO console: <http://localhost:9001>

```bash
curl --fail http://localhost:8080/api/health
curl --fail http://localhost:8000/health
curl --fail http://localhost:5173/
docker compose ps
```

Follow logs with `docker compose logs -f`, or narrow them with commands such as `docker compose logs -f backend` and `docker compose logs -f caption-worker`. Stop containers without deleting application data using:

```bash
docker compose stop
```

`make down` removes the containers and network but preserves the named PostgreSQL and MinIO volumes.

### Verification

Run every non-GPU automated check, including backend integration/migration/module checks, frontend lint/type/component/build checks, worker tests, MinIO bootstrap/configuration checks, and Compose validation:

```bash
make verify
```

Run the two publication journeys and axe checks against a Compose stack. This command builds and starts the stack, waits for health, runs Playwright serially, and always stops the containers while preserving both named volumes:

```bash
make e2e
```

`make e2e` uses bundled Chromium by default. To explicitly use a locally installed Chrome for a manual assistive-technology run, use `CANVAS_E2E_BROWSER_CHANNEL=chrome make e2e`.

To prove that the published database records, original image, audio, and QR asset survive an ordinary restart:

```bash
docker compose restart
make e2e-persistence-check
```

The persistence target expects the manual E2E publication created by `make e2e`. See [Manual testing](docs/manual-testing.md) for the keyboard, screen-reader, zoom, and content-safety release checks.

### Opt-in local data reset

Reset is intentionally destructive and is never part of `down` or a normal E2E teardown. The unconfirmed command exits without asking Docker to delete anything:

```bash
make reset-local-data
```

To delete only this Compose project's two named volumes (`canvas-postgres-data` and `canvas-minio-data`) and all local uploads/publications/cached assets they contain:

```bash
CONFIRM=1 make reset-local-data
```

## Technology direction

- React and TypeScript for the browser experience
- Spring Boot and Java for workflow authority and API orchestration
- PostgreSQL for application state
- A Python caption worker behind a typed HTTP contract; the current worker is deterministic and model-free
- Private S3-compatible object storage for artwork originals and generated assets
- Replaceable audio and QR generators invoked only from approved publication input; the current audio generator returns a generic placeholder WAV
- Future evaluation of a real caption model, GPU runtime or execution provider, and text-specific audio generator

No production model, GPU provider, hosting vendor, or text-to-speech service is selected. Any adoption must be validated against cost, reliability, accessibility, privacy, security, and operational requirements while preserving the existing contracts and human-approval rules.

## Documentation

- [Architecture](docs/architecture.md)
- [Product Scope](docs/product-scope.md)
- [Roadmap](docs/roadmap.md)
- [Cost Principles](docs/cost-principles.md)
- [Local MVP Design Record](docs/superpowers/specs/2026-07-21-canvas-local-mvp-design.md)
- [Architecture Decision Records](docs/decisions/README.md)
- [Codex and contributor guidance](AGENTS.md)

## Collaboration

CANVAS is being developed as an open, portfolio-quality project with the ambition to become a useful production service. Partnership conversations, user research, accessibility review, and nonprofit handoff planning will influence the roadmap as the product matures.

No generated description should be treated as a substitute for feedback from blind and low-vision users, accessibility professionals, artists, or trained describers.
