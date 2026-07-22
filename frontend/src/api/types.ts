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
