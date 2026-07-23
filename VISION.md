# CANVAS vision

## Why CANVAS exists

Visual artwork is often exhibited or published without useful descriptions for blind and low-vision audiences. CANVAS exists to make preparing and publishing trustworthy visual descriptions a sustainable part of presenting art, while preserving the judgment and accountability that accessible editorial work requires.

## Who CANVAS serves

CANVAS serves blind and low-vision people who use visual descriptions, as well as the artists, museums, galleries, community organizations, and other people preparing accessible content. Blind and low-vision people must remain involved in evaluating whether the descriptions and the experience are useful.

## What makes CANVAS different

CANVAS is an editorial publication workflow, not a generic image-captioning tool. Machine-generated content is optional draft material and never publication authority. It can help an editor begin, but it does not replace context, careful wording, human review, or accountability for what becomes public.

## The editorial workflow

An artwork can have zero or more ordered descriptions with free-form labels. Descriptions can be entered manually, generated as drafts, or imported through a future workflow. Manual and generated descriptions follow the same revisioned editorial process.

Humans review and explicitly approve exact revisions before publication. Publication records the approved wording and order as an immutable snapshot. Approved public content remains available as text even when audio exists, and QR codes provide a physical-to-digital path from an exhibited artwork to its public page.

## Non-negotiable principles

- **Human approval:** generated output remains a draft until a person explicitly approves the exact revision that may be published.
- **Factual and respectful descriptions:** content distinguishes visible details from interpretation and does not invent intent, identity, demographic attributes, or emotional meaning.
- **Accessible operation:** CANVAS must support people using keyboard, screen-reader, zoom, and other access strategies, while public descriptions remain available as text.
- **Privacy and consent:** artwork use, publication, retention, and access controls must be handled deliberately.
- **Auditable publication:** revision and publication history must show exactly what was approved and made public.
- **Affordable operation:** architecture and service choices must remain plausible for small nonprofits with limited budgets and technical staff.
- **Replaceable boundaries:** caption models and storage providers remain behind contracts that allow them to change without redefining the editorial workflow.

## Product boundaries

CANVAS is not a general content management system, digital asset manager, image editor, marketplace, social network, or automatic publishing engine. The current MVP is admin-only. Broader organizational workflows are future product questions rather than implied current capabilities.

## What success looks like

CANVAS succeeds when organizations can publish trustworthy visual descriptions without needing an internal machine-learning team or cloud engineers, and when blind and low-vision people participate meaningfully in evaluating the descriptions and the experience. Success also requires that editors understand and control what is approved, published, and retained.

## Current status

This repository currently contains an admin-only local demo of the editorial workflow. It uses deterministic placeholder caption behavior that does not analyze images and placeholder WAV audio that does not narrate approved text. The demo is not a production-ready service, and it has not completed full human accessibility validation. Production deployment, a real caption model, text-specific audio generation, and the outstanding manual keyboard, audible screen-reader, zoom/reflow, and contrast checks remain future work.

## Related documents

- [Project overview and local operation](README.md)
- [Product scope](docs/product-scope.md)
- [Architecture](docs/architecture.md)
- [Cost principles](docs/cost-principles.md)
- [Roadmap](docs/roadmap.md)
- [Architecture decision records](docs/decisions/README.md)
- [Manual accessibility acceptance](docs/manual-testing.md)
