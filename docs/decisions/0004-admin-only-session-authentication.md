# ADR 0004: Admin-Only Session Authentication

**Status:** Accepted

**Decision date:** 2026-07-21

**Recorded date:** 2026-07-22

## Context

The local MVP needs to protect every administrative operation while keeping account management outside its scope. This retrospective record documents the authentication choice implemented on 2026-07-21.

Public artwork and generated-asset routes remain anonymous, but draft editing, approval, publication, and other administrative routes require an authenticated administrator.

## Decision

The MVP has one administrator configured through environment values. The configured password is stored as a BCrypt hash rather than plaintext. Spring Security establishes a server-side session and identifies it with an HTTP-only session cookie. State-changing requests require CSRF protection, and authentication and authorization failures from API routes use JSON 401 and 403 responses.

Registration, organizations, roles, password recovery, and external identity providers are explicitly deferred. They are not implied by the configured administrator account.

## Alternatives considered

- No authentication. This would simplify a demo but leave approval and publication operations unprotected.
- HTTP Basic authentication. This avoids session state but gives the browser a poorer sign-in and sign-out model and repeatedly sends credentials.
- Token or JWT authentication. This supports stateless APIs but adds token issuance, storage, expiry, and revocation concerns that the local single-admin MVP does not need.
- An external identity provider. This could support production identity features but would select a provider, require network configuration, and increase cost and setup before those requirements are known.

## Consequences

The local demo has a small authentication surface and no account database. The backend must retain server-side session state, and operators must handle the administrator username and BCrypt hash as environment secrets.

Because all actions belong to one configured administrator, the MVP does not provide distinct multi-user audit identity, organization ownership, invitation, or recovery workflows.

## Reversal or migration path

Persistent users and organizations can be introduced with forward database migrations and a replacement authentication configuration. Existing domain ownership, approver, publisher, and audit fields must be preserved or mapped to migrated identities so historical editorial actions remain attributable.

## References

- [ADR 0001: Project Foundation](0001-project-foundation.md)
- [Product scope](../product-scope.md)
- [CANVAS local MVP design](../superpowers/specs/2026-07-21-canvas-local-mvp-design.md)
- [CANVAS local MVP implementation plan](../superpowers/plans/2026-07-21-canvas-local-mvp.md)
