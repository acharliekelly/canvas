export interface SessionResponse {
  authenticated: boolean;
  username: string | null;
  csrfToken: string;
}

export interface ArtworkSummary {
  id: string;
  title: string;
  credit: string;
  status: "UPLOADED";
  createdAt: string;
}

export interface ArtworkDetail extends ArtworkSummary {
  context: string | null;
  mediaType: string;
  byteSize: number;
  version: number;
  updatedAt: string;
}

export interface ApiProblem {
  status: number;
  title: string;
  detail: string;
  code?: string;
  field?: string;
}
