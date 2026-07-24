# Java 25 Backend Health-Check Compatibility Design

## Context

The backend was upgraded from `eclipse-temurin:21-jre` to
`eclipse-temurin:25-jre`. The application starts successfully on Java 25, but
the Java 25 runtime image does not contain `wget`. The Compose backend health
check invokes `wget`, so Docker records 20 failures with:

```text
/bin/sh: 1: wget: not found
```

Docker then marks the running backend unhealthy, preventing the frontend and
Playwright end-to-end tests from starting.

## Decision

The backend runtime stage will explicitly install `wget` and the system CA
certificate bundle. The package-manager cache will be removed in the same
Docker layer.

The existing Compose health-check command and `/api/health` contract will
remain unchanged. Making the health-check dependency explicit in the image
keeps the runtime image self-contained and avoids relying on incidental tools
provided by a particular Temurin release.

## Alternatives Considered

### Replace `wget` with `curl`

The Java 25 runtime image also does not provide a guaranteed `curl` executable,
so this approach would still require installing a package and would also change
the established health-check command.

### Add a custom Java health-check program

A dedicated Java class or source-file invocation could make the request without
an external HTTP client. This was rejected because it adds application code and
startup complexity for a container concern already handled by a small standard
utility.

### Use a TCP-only health check

A socket-level check would prove only that the port accepts connections, not
that the application reports its readiness contract. It would weaken the
current health semantics.

## Testing

Before changing the Dockerfile, the built Java 25 runtime image will reproduce
the failure by attempting to execute `wget --version`.

After the change:

- the runtime image will successfully execute `wget --version`;
- `make e2e` will require the backend `/api/health` check to pass before
  Playwright starts; and
- the complete publication journeys will verify the combined Java 25,
  namespace, MinIO, backend, and frontend stack.

No application API, persistence, or editorial behavior changes.

## Consequences and Reversal

The runtime image gains the size and patching responsibility of `wget` and the
CA certificate package. Pinning the base image remains the primary runtime
reproducibility boundary; ordinary image rebuilds receive the package versions
available from that base distribution's configured repositories.

The change can be reversed by removing the package-install layer after replacing
the Compose health check with another readiness mechanism that preserves the
same HTTP readiness semantics.
