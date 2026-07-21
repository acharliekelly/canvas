# ADR 0001: Project Foundation

**Status:** Accepted

**Date:** 2026-07-21

---

# Context

CANVAS (Captioning and Narration for Visual Accessibility Services) is an accessibility platform that helps museums, galleries, artists, nonprofits, and similar organizations publish high-quality descriptions of visual artwork.

The project exists to improve access for blind and low-vision visitors by combining AI-assisted caption generation with human editorial review.

Although AI models accelerate content creation, accessibility descriptions require accuracy, nuance, and accountability. CANVAS is therefore designed as an editorial workflow rather than an automated publishing system.

This document records the foundational architectural decisions that guide the project.

---

# Decision 1: Spring Boot Backend

## Decision

The primary application backend will be implemented using Spring Boot.

## Rationale

- Strong support for layered architecture and domain modeling.
- Excellent testing ecosystem.
- Long-term maintainability.
- Valuable experience for professional software engineering roles.
- Mature ecosystem for security, persistence, scheduling, and REST APIs.

## Alternatives Considered

- Node.js / Express
- Django
- FastAPI

## Consequences

Business logic remains centralized in the Java application while machine learning remains independent.

---

# Decision 2: React Frontend

## Decision

The web client will be implemented in React.

## Rationale

- Mature ecosystem.
- Strong accessibility tooling.
- Component-driven UI.
- Existing project experience.

## Alternatives Considered

- Angular
- Vue
- Server-rendered templates

## Consequences

The frontend communicates exclusively through documented REST APIs.

---

# Decision 3: Separate Python Caption Worker

## Decision

Image caption generation will execute in an independent Python service.

## Rationale

- ML tooling is strongest in Python.
- Avoids introducing Python dependencies into the Java application.
- Allows caption models to evolve independently.
- Makes future replacement of JoyCaption straightforward.

## Alternatives Considered

- Embed Python inside Spring Boot.
- Java ML libraries.
- Entire application written in Python.

## Consequences

The backend communicates with a stable caption-generation interface rather than a specific model implementation.

---

# Decision 4: Human Review Before Publication

## Decision

AI-generated descriptions are drafts.

Publication requires explicit human approval.

## Rationale

- Accessibility descriptions require editorial quality.
- AI systems may hallucinate or omit important details.
- Museums and artists require editorial control.
- Human accountability builds trust.

## Alternatives Considered

- Automatic publication
- Optional review

## Consequences

Every published description has a clear approval workflow.

---

# Decision 5: Modular Monolith

## Decision

CANVAS will begin as a modular monolith.

## Rationale

- Lower operational complexity.
- Easier development.
- Better fit for expected project size.
- Appropriate for nonprofit budgets.

## Alternatives Considered

- Microservices
- Event-driven distributed architecture

## Consequences

Modules remain logically separated without requiring independent deployment.

---

# Decision 6: S3-Compatible Object Storage

## Decision

Artwork and generated assets will be stored using an S3-compatible abstraction.

## Rationale

- Avoid vendor lock-in.
- Broad cloud compatibility.
- Industry-standard APIs.
- Supports self-hosted and managed providers.

## Alternatives Considered

- Local filesystem
- Cloudinary
- Vendor-specific storage APIs

## Consequences

Storage providers can change without affecting application logic.

---

# Decision 7: Scale-to-Zero GPU Inference

## Decision

GPU resources should only run while actively generating captions.

## Rationale

- GPU infrastructure is the primary operating expense.
- Most organizations generate descriptions infrequently.
- Nonprofit budgets favor usage-based costs.

## Alternatives Considered

- Permanently running GPU server
- Dedicated inference cluster

## Consequences

Caption generation becomes an asynchronous background operation.

---

# Decision 8: Accessibility Is a Product Requirement

## Decision

Accessibility requirements apply to both generated content and the application itself.

## Rationale

CANVAS should demonstrate the accessibility principles it promotes.

## Consequences

Accessibility is considered complete only after both automated and manual verification.

---

# Decision 9: AI Model Independence

## Decision

JoyCaption is the initial caption engine, not a permanent dependency.

## Rationale

The field evolves rapidly.

Future improvements may include:

- JoyCaption updates
- Fine-tuned models
- Commercial APIs
- Organization-specific models

## Consequences

The application depends on a caption-generation contract rather than a specific implementation.

---

# Decision 10: Cost-Conscious Architecture

## Decision

Operating cost is treated as a functional requirement.

## Rationale

CANVAS is intended to be accessible to organizations with limited technical staff and limited budgets.

Infrastructure decisions should favor simplicity, predictable costs, and operational sustainability.

## Consequences

Future features should justify their operational cost and avoid unnecessary complexity.

---

# Guiding Principles

Future architectural decisions should reinforce the following principles:

- Accessibility before convenience.
- Human expertise over automated publishing.
- Simple systems before distributed systems.
- Replaceable components over vendor lock-in.
- Sustainable operating costs.
- Clear boundaries between UI, business logic, and machine learning.
- AI augments human expertise rather than replacing it.

---

# Revision History

| Date | Change |
|------|--------|
| 2026-07-21 | Initial project foundation established. |