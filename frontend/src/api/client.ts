import type { ApiProblem } from "./types";

let csrfToken: string | null = null;

export class ApiError extends Error {
  constructor(public readonly status: number, message: string, public readonly code?: string,
    public readonly field?: string) {
    super(message);
    this.name = "ApiError";
  }
}

export function setCsrfToken(token: string) {
  csrfToken = token;
}

/**
 * Calls same-origin APIs with session credentials. Mutation requests send the current CSRF token
 * issued by `/api/session` as `X-CSRF-TOKEN`; a session response refreshes the cached token.
 * Empty successful responses resolve as `undefined`; other successful JSON responses are returned
 * as the requested type. RFC problem JSON becomes an `ApiError` with status, code, and field, while other
 * failures receive a safe fallback message. Requests are not automatically retried.
 */
export async function apiFetch<T>(path: string, init: RequestInit = {}): Promise<T> {
  const headers = new Headers(init.headers);
  const method = (init.method ?? "GET").toUpperCase();
  if (!headers.has("Accept")) headers.set("Accept", "application/json, application/problem+json");
  if (csrfToken && !["GET", "HEAD", "OPTIONS"].includes(method)) headers.set("X-CSRF-TOKEN", csrfToken);

  const response = await fetch(path, { ...init, headers, credentials: "same-origin" });
  const body = await readBody(response);
  if (!response.ok) {
    const problem = body as Partial<ApiProblem> | null;
    throw new ApiError(response.status, problem?.detail ?? "The request could not be completed.", problem?.code,
      problem?.field);
  }
  if (body && typeof body === "object" && "csrfToken" in body && typeof body.csrfToken === "string") {
    setCsrfToken(body.csrfToken);
  }
  return body as T;
}

async function readBody(response: Response): Promise<unknown> {
  if (response.status === 204) return undefined;
  const text = await response.text();
  if (!text) return undefined;
  try {
    return JSON.parse(text);
  } catch {
    return undefined;
  }
}
