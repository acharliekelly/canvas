import { useEffect, useState } from "react";

import { apiFetch } from "../api/client";
import type { ArtworkSummary } from "../api/types";
import { ArtworkUploadForm } from "./ArtworkUploadForm";

export function ArtworkListPage() {
  const [artworks, setArtworks] = useState<ArtworkSummary[] | null>(null);
  const [error, setError] = useState(false);

  useEffect(() => {
    let active = true;
    apiFetch<ArtworkSummary[]>("/api/artworks")
      .then((loaded) => { if (active) setArtworks(loaded); })
      .catch(() => { if (active) setError(true); });
    return () => { active = false; };
  }, []);

  return (
    <div className="admin-layout">
      <ArtworkUploadForm onUploaded={(artwork) => setArtworks((current) => [artwork, ...(current ?? [])])} />
      <section aria-labelledby="artwork-list-heading">
        <h2 id="artwork-list-heading">Artworks</h2>
        {error && <p role="alert">The artwork list could not be loaded.</p>}
        {artworks === null && !error && <p role="status">Loading artworks</p>}
        {artworks?.length === 0 && <p>No artworks uploaded yet.</p>}
        {artworks && artworks.length > 0 && <ul className="artwork-list">{artworks.map((artwork) => <li key={artwork.id}><h3><a href={`/artworks/${artwork.id}/edit`}>{artwork.title}</a></h3><p>{artwork.credit}</p><p><span className="status-label">Status:</span> {artwork.status}</p></li>)}</ul>}
      </section>
    </div>
  );
}
