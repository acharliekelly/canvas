# CANVAS

**Captioning and Narration for Visual Accessibility Services**

CANVAS is an accessibility platform for creating, reviewing, publishing, and sharing rich visual descriptions of artwork. The project is designed to help artists, galleries, museums, and community organizations make visual art more accessible to blind and low-vision audiences.

CANVAS uses machine-generated descriptions as drafts, not final authority. A human reviewer edits and approves each description before publication. Approved descriptions can then be presented as text, converted to audio, and linked from a printable QR code placed beside the artwork.

## Project status

CANVAS includes a local, admin-operated MVP that proves the complete workflow:

1. Upload an artwork image.
2. Generate a draft description with a visual-language model.
3. Review and edit the draft.
4. Approve the final description.
5. Generate and cache audio narration.
6. Publish an accessible artwork page.
7. Create a QR code linking to that page.

The MVP is not a production service. It uses a deterministic placeholder caption worker and placeholder WAV narration; no image model, GPU, cloud account, or paid service is required.

## Why CANVAS

Many artworks are exhibited without meaningful visual descriptions. Existing alternatives may require specialized staff, expensive vendors, or substantial manual effort. CANVAS aims to reduce that production burden while preserving human judgment, artistic context, and editorial responsibility.

The project is being developed with potential nonprofit and accessibility-sector partnerships in mind. Affordability is a product requirement, not an afterthought: the intended operating model should remain plausible for an organization whose available monthly technology budget may be measured in tens of dollars.

## Planned architecture

CANVAS is expected to use a modular monorepo:

```text
canvas/
├── frontend/        React administration and public experience
├── backend/         Spring Boot API and workflow orchestration
├── caption-worker/  Python inference service using JoyCaption or a successor
├── infrastructure/  Local development and deployment configuration
└── docs/            Product, architecture, cost, and decision records
```

At a high level:

```text
React frontend
      |
Spring Boot API
      |
PostgreSQL + S3-compatible object storage
      |
background caption jobs
      |
on-demand Python GPU worker
      |
human review and approval
      |
text, audio, public artwork page, and QR code
```

The caption engine is intentionally isolated behind an interface so that CANVAS is not permanently coupled to JoyCaption, a particular GPU provider, or any single cloud vendor.

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
- Java 21, Node.js 24 with npm, and Python 3.13 for host-side verification;
- GNU Make and curl;
- Google Chrome for the default Playwright configuration. To use Playwright's bundled Chromium instead, run `cd frontend && npx playwright install chromium`, then set `CANVAS_E2E_BROWSER_CHANNEL=bundled` when running E2E commands.

Copy the local sample configuration. `.env` is ignored by Git.

```bash
cp .env.example .env
```

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

Run every non-GPU automated check, including backend integration/migration/module checks, frontend lint/type/component/build checks, all worker tests, and Compose validation:

```bash
make verify
```

Run the two publication journeys and axe checks against a Compose stack. This command builds and starts the stack, waits for health, runs Playwright serially, and always stops the containers while preserving both named volumes:

```bash
make e2e
```

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

- React and TypeScript
- Spring Boot and Java
- PostgreSQL
- Python caption worker
- JoyCaption as the initial captioning engine
- S3-compatible object storage
- On-demand GPU compute, initially evaluated with RunPod
- Text-to-speech and QR generation after editorial approval

Specific vendors remain replaceable until validated by cost, reliability, accessibility, and operational requirements.

## Documentation

- [Architecture](docs/architecture.md)
- [Product Scope](docs/product-scope.md)
- [Roadmap](docs/roadmap.md)
- [Cost Principles](docs/cost-principles.md)
- [Local MVP Design Record](docs/superpowers/specs/2026-07-21-canvas-local-mvp-design.md)
- [Codex and contributor guidance](AGENTS.md)

## Collaboration

CANVAS is being developed as an open, portfolio-quality project with the ambition to become a useful production service. Partnership conversations, user research, accessibility review, and nonprofit handoff planning will influence the roadmap as the product matures.

No generated description should be treated as a substitute for feedback from blind and low-vision users, accessibility professionals, artists, or trained describers.
