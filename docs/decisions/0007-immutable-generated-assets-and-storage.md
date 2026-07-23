# ADR 0007: Immutable Generated Assets and Storage

**Status:** Accepted

**Decision date:** 2026-07-21

**Recorded date:** 2026-07-22

## Context

Audio and QR generation can incur compute or provider cost, and mutable generated files would make a historical publication depend on whichever bytes happen to occupy a stable path. Original artwork also has different access and lifecycle concerns from public generated assets. This retrospective record documents the asset and storage design implemented on 2026-07-21.

The local MVP uses deterministic placeholder audio and local S3-compatible storage, but its identity and publication rules must remain valid when generators or storage providers change.

## Decision

Audio derives from the exact approved text in a publication, and QR codes derive from the exact stable publication URL. Generated-asset metadata and stored objects are cached under deterministic content identities that include the generator namespace and relevant input. A publication snapshot persists the exact audio association for each published description and the exact QR association for the publication.

Public generated-asset routes contain the generated-asset UUID and return immutable cache headers. They resolve the snapshot association before serving an object, so an older association is not silently redirected to newer bytes.

If cached metadata exists but its object is missing, generation repairs the object at the same content-addressed key while holding a transaction-scoped same-key lock. Repair retains the metadata identity instead of creating a duplicate row; database uniqueness also enforces one metadata record per asset kind and input key.

Originals and generated assets use separate private S3-compatible buckets. MinIO root credentials are used only by the bootstrap initializer. The backend authenticates with a distinct application identity whose policy is scoped to the two configured buckets.

## Alternatives considered

- Regenerate assets on every request. This avoids cache metadata but repeats cost and can produce inconsistent bytes over time.
- Use stable mutable asset aliases. This provides short URLs but allows cached or historical publications to serve replacement content without a new association.
- Store binary data in database blobs or a local filesystem. This reduces local services but couples binary scale and portability to the application host or database.
- Use one shared bucket. This is simpler to configure but weakens separation between private originals and generated assets.
- Let the backend use MinIO root credentials. This avoids provisioning an application identity but grants unnecessary administrative authority to the runtime application.

## Consequences

Published assets are reproducible and tied to exact public provenance. Reuse lowers repeated generation cost, while separate private buckets and a scoped backend identity improve isolation.

The system must maintain content-key rules, metadata-to-object consistency, publication associations, and repair locking. Historical assets and metadata accumulate and need an explicit retention or migration policy if storage growth becomes material.

## Reversal or migration path

New generators, storage providers, or key namespaces can be introduced through versioned metadata and forward migrations. Existing asset associations and UUID-bearing public URLs must continue resolving to their original bytes, or be migrated with an explicit compatibility layer, while new publications adopt the new namespace.

## References

- [ADR 0001: Project Foundation](0001-project-foundation.md)
- [Architecture](../architecture.md)
- [Cost principles](../cost-principles.md)
- [CANVAS local MVP design](../superpowers/specs/2026-07-21-canvas-local-mvp-design.md)
- [CANVAS local MVP implementation plan](../superpowers/plans/2026-07-21-canvas-local-mvp.md)
