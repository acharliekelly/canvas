# MinIO Bootstrap Retry Design

## Context

The local Compose stack waits until MinIO reports healthy before starting the
one-shot `minio-init` service. Docker's internal DNS can nevertheless fail
transiently when `minio-init` first resolves the `minio` service name. The
initializer currently makes one `mc alias set` attempt, so a transient lookup
failure aborts startup before the backend or end-to-end tests can run.

This failure reproduced on two consecutive `make e2e` runs with:

```text
lookup minio on 127.0.0.11:53: server misbehaving
```

MinIO itself reached its healthy state in both runs.

## Decision

`infrastructure/minio/create-bucket.sh` will retry only the initial
authenticated `mc alias set` operation. It will:

- make at most 10 attempts;
- wait one second between failed attempts;
- report failed attempts and the remaining retry activity;
- continue immediately after the first successful connection; and
- exit nonzero after the final failed attempt.

Bucket creation, anonymous-access removal, backend-user management, and policy
management will remain single-attempt operations. Those commands run only after
the authenticated MinIO connection succeeds, and their failures are more likely
to identify configuration or authorization defects that should remain visible.

The retry will not reset Compose volumes, replace credentials, or weaken the
existing private-bucket and least-privilege policy behavior.

## Alternatives Considered

### Compose restart policy

A restart policy on `minio-init` could rerun the complete initializer after any
failure. This was rejected because it would also retry persistent bucket,
credential, and policy errors, delaying and obscuring actionable failures.

### Separate DNS readiness probe

The script could wait only for the `minio` hostname to resolve before invoking
`mc`. This was rejected because DNS resolution alone does not prove that MinIO
is ready to accept an authenticated request, while retrying `mc alias set`
checks the actual boundary the initializer needs.

### No change and manual retry

Operators could rerun `make e2e` after transient failures. This was rejected
because the same failure reproduced twice and makes the documented local
workflow unreliable.

## Testing

`infrastructure/minio/configuration-test.sh` will use its fake `mc` executable
to fail the first two alias attempts and succeed on the third. A fake `sleep`
will keep the test fast. Assertions will prove:

- exactly three alias attempts occurred;
- retry delays occurred only between failed attempts; and
- bucket, user, and policy initialization continued exactly once after the
  successful alias attempt.

The implementation will then be verified with `make verify` and `make e2e`.
No local data reset is required.

## Consequences and Reversal

Transient Docker DNS or MinIO readiness failures can add at most nine seconds
to startup before producing a final failure. Persistent configuration failures
after connection remain immediate.

The change is isolated to the local MinIO bootstrap boundary. It can be
reversed by restoring the single `mc alias set` call if future Compose or Docker
behavior makes the retry unnecessary.
