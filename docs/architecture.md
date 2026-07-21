# Purpose

CANVAS is an accessibility platform that helps organizations publish high-quality visual descriptions for artwork.

CANVAS is **not** an AI image captioning application. AI is one component of the platform.


# Design Principles

- Accessibility is a product requirement.
- Human review is required before publication.
- Components should be independently replaceable.
- Infrastructure should remain affordable for nonprofits.
- Prefer simple architectures over distributed complexity.
- AI augments editors rather than replacing them.


# System Diagram

React
   │
REST API
   │
Spring Boot
   ├─────────────┐
   │             │
Postgres     Object Storage
   │             │
   └─────Jobs────┘
         │
Caption Worker
(JoyCaption today,
replaceable tomorrow)


# Component Responsibilities

frontend/

backend/

caption-worker/

infrastructure/


# Data Flow

Upload

↓

Generate draft

↓

Human review

↓

Approval

↓

Generate audio

↓

Generate QR

↓

Publish


# Future Architecture

Additional services (search, analytics, ML training, etc.) should only appear when justified.

