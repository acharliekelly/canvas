# Cost principles

## Purpose

CANVAS prioritizes small nonprofits and predictable operations. Cost is a product and architecture constraint: an organization should be able to understand the system's recurring and usage-driven expenses before depending on it.

The aspirational steady-state idle target is below USD 50 per month. This target is not a quote, budget commitment, or permanent guarantee. Actual production cost will depend on deployment choices, workload, storage, traffic, and pricing available at the time of review.

## Operating assumptions

- Description work may be intermittent, so expensive compute should not run continuously while unused.
- Small organizations may not have cloud engineering staff to tune or troubleshoot infrastructure.
- Generated outputs can often be reused rather than regenerated.
- Production usage, retention, and traffic are not yet known from the local demo and must be measured before commitments are made.
- Production free tiers may change or disappear and cannot define the production cost model.

## Cost model

Costs must be considered as baseline capacity plus usage-driven growth. Estimates should state their pricing assumptions and expected workload so reviewers can see which costs remain steady and which scale with use.

### Fixed or baseline costs

Likely baseline categories are application hosting, managed or self-operated PostgreSQL, and object storage. The final balance between managed and self-operated infrastructure is undecided; reviews must account for both the invoice and the operating effort a choice creates.

### Variable costs

Variable categories include caption inference, future audio generation, bandwidth and egress, storage growth, and growth in batch workloads. Variable cost per job or artwork should be observable where practical so organizations can connect workload changes to their bill.

## Architectural implications

- GPU inference should scale to zero when idle.
- Generated audio, captions, thumbnails, and QR assets should be cached and reused when their inputs and publication associations permit it.
- Idle infrastructure must not dominate the cost expected from actual use.
- Object storage remains behind a private S3-compatible abstraction so storage providers can be replaced without changing application workflow.
- Components with meaningful variable cost should expose enough operational data to estimate cost per job or artwork where practical.
- New permanent workers or deployable services require evidence that their value justifies their baseline cost and operational burden.

## Requirements for paid managed services

A proposal for a paid managed service must document:

- expected cost at the stated workload;
- pricing assumptions and the date or source used for them;
- free-tier limitations, including the effect of the free tier changing or ending;
- simpler or less expensive alternatives;
- portability and an exit path for data, configuration, and application integration; and
- conditions that will trigger another cost and architecture review.

This guidance does not select a hosting, database, object-storage, inference, audio, or other production provider.

## Cost review triggers

Revisit the cost model when CANVAS introduces a new paid vendor, adds a permanently running worker, experiences material storage or egress growth, changes its workload pattern, or moves from a local demo toward production. Review is also warranted when pricing assumptions change enough to threaten predictable operation or the aspirational idle target.

## Definition of success

Cost design succeeds when a small organization can operate CANVAS without cloud engineering staff, understand what drives its bill, and replace a component whose price or operating burden no longer fits. Success is measured through transparent assumptions and observed usage, not through a guaranteed monthly price.

## Related documents

- [Project overview and local operation](../README.md)
- [Product scope](product-scope.md)
- [Architecture](architecture.md)
- [Roadmap](roadmap.md)
- [Architecture decision records](decisions/README.md)
