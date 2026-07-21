# CANVAS

**Captioning and Narration for Visual Accessibility Services**

CANVAS is an accessibility platform for creating, reviewing, publishing, and sharing rich visual descriptions of artwork. The project is designed to help artists, galleries, museums, and community organizations make visual art more accessible to blind and low-vision audiences.

CANVAS uses machine-generated descriptions as drafts, not final authority. A human reviewer edits and approves each description before publication. Approved descriptions can then be presented as text, converted to audio, and linked from a printable QR code placed beside the artwork.

## Project status

CANVAS is in early product and architecture development. The first release will be an admin-operated MVP that proves the complete workflow:

1. Upload an artwork image.
2. Generate a draft description with a visual-language model.
3. Review and edit the draft.
4. Approve the final description.
5. Generate and cache audio narration.
6. Publish an accessible artwork page.
7. Create a QR code linking to that page.

The MVP is not yet a production service. The repository is being structured from the beginning as a credible, maintainable application with a path toward nonprofit ownership and public deployment.

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
- [Foundation Design Record](docs/superpowers/specs/2026-07-21-canvas-foundation-design.md)
- [Codex and contributor guidance](AGENTS.md)

## Collaboration

CANVAS is being developed as an open, portfolio-quality project with the ambition to become a useful production service. Partnership conversations, user research, accessibility review, and nonprofit handoff planning will influence the roadmap as the product matures.

No generated description should be treated as a substitute for feedback from blind and low-vision users, accessibility professionals, artists, or trained describers.
