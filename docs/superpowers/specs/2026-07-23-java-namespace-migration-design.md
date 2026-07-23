# Java Namespace Migration Design

## Goal

Replace the backend's former placeholder namespace with the canonical
`me.acharliekelly.canvas` namespace everywhere in the repository and update
pull request #4 without changing application behavior.

## Scope

The migration is repository-wide:

- move the former production Java package tree to
  `backend/src/main/java/me/acharliekelly/canvas/`;
- move the former Java test package tree to
  `backend/src/test/java/me/acharliekelly/canvas/`;
- replace package declarations and imports in production and test code;
- change the backend Maven project `groupId` to `me.acharliekelly.canvas`;
- update every textual reference to the former namespace and package path,
  including historical design and implementation documents.

The migration does not rename classes, modules, endpoints, database objects,
configuration keys, Docker services, JavaScript or Python packages, or the
Maven artifact ID.

## Implementation

Perform the source-tree moves and textual replacements as one atomic change so
the repository never intentionally supports both namespaces. Preserve file
contents except for namespace references and any formatting required by the
move.

Spring Boot component scanning continues from `CanvasApplication` in the new
root package. Spring Modulith module discovery and architecture tests migrate
with the same root, preserving the existing module boundaries.

## Compatibility

CANVAS is a greenfield local MVP with no published Java library contract or
persisted fully qualified class names. The Java package and Maven coordinate
change therefore requires no compatibility alias, deprecation bridge, data
migration, or ADR. Runtime HTTP, persistence, storage, and worker contracts
remain unchanged.

## Verification

Verification must establish both functional equivalence and migration
completeness:

1. repository search finds no remaining reference to the former namespace or
   package path;
2. production and test files exist only under the new package path;
3. Maven compilation and JavaDoc generation succeed;
4. the complete backend verification suite passes, including module,
   migration, PostgreSQL, and MinIO integration tests;
5. full repository verification passes with Node 24 and Python 3.13;
6. Compose configuration and whitespace checks pass.

The change is committed and pushed to the existing
`docs/code-documentation` branch so pull request #4 updates in place.
