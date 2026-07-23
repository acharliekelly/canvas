export interface SessionResponse {
  authenticated: boolean;
  username: string | null;
  csrfToken: string;
}

export interface ArtworkSummary {
  id: string;
  title: string;
  credit: string;
  status: "UPLOADED" | "PUBLISHED";
  createdAt: string;
}

export interface ArtworkDetail extends ArtworkSummary {
  context: string | null;
  mediaType: string;
  byteSize: number;
  version: number;
  updatedAt: string;
}

export type DescriptionSource = "MANUAL" | "GENERATED";
export type RevisionState = "DRAFT" | "APPROVED";

export interface DescriptionRevisionResponse {
  revisionId: string;
  label: string;
  text: string;
  state: RevisionState;
  parentRevisionId: string | null;
  approvedBy: string | null;
  approvedAt: string | null;
  createdAt: string;
  updatedAt: string;
}

/**
 * The current revision and append-only revision history for one description. `approvedRevisionId`
 * can identify retained approved history while `currentRevision` is a newer draft; `version` is
 * the optimistic-concurrency value for description mutations.
 */
export interface DescriptionResponse {
  descriptionId: string;
  artworkId: string;
  source: DescriptionSource;
  displayOrder: number;
  version: number;
  currentRevision: DescriptionRevisionResponse;
  approvedRevisionId: string | null;
  revisions: DescriptionRevisionResponse[];
  createdAt: string;
  updatedAt: string;
}

export type CaptionJobState = "PENDING" | "RUNNING" | "SUCCEEDED" | "FAILED";

/**
 * Polling state for a caption request. `SUCCEEDED` and `FAILED` are terminal; `attemptCount`
 * identifies retries, while a successful job may expose its generated description and a failed job
 * exposes only a sanitized error message.
 */
export interface CaptionJobResponse {
  jobId: string;
  artworkId: string;
  state: CaptionJobState;
  attemptCount: number;
  errorMessage: string | null;
  resultingDescriptionId: string | null;
  version: number;
  createdAt: string;
  startedAt: string | null;
  completedAt: string | null;
  updatedAt: string;
}

/**
 * The publication response reports whether a new immutable snapshot was `created`, the artwork
 * version to use next, the current snapshot's descriptions, and its asset-ID-versioned `qrUrl`.
 */
export interface PublicationResult {
  publicationId: string;
  slug: string;
  qrUrl: string;
  publishedAt: string;
  artworkVersion: number;
  created: boolean;
  descriptions: PublishedDescription[];
}

export interface PublishedDescription {
  label: string;
  text: string;
}

/**
 * The public view exposes only the current immutable publication snapshot: drafts and admin
 * metadata are excluded. `audioUrl` can be null for conservatively upgraded legacy snapshots.
 */
export interface PublicArtworkResponse {
  title: string;
  credit: string;
  imageUrl: string;
  qrUrl: string | null;
  descriptions: PublicDescription[];
}

export interface PublicDescription extends PublishedDescription {
  audioUrl: string | null;
}

export interface ApiProblem {
  status: number;
  title: string;
  detail: string;
  code?: string;
  field?: string;
}
