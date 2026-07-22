import { useEffect, useState } from "react";

import { apiFetch } from "../api/client";
import type { ArtworkDetail, DescriptionResponse } from "../api/types";
import { DescriptionEditor } from "./DescriptionEditor";

interface EditorData {
  artwork: ArtworkDetail;
  descriptions: DescriptionResponse[];
}

export function ArtworkEditorPage({ artworkId }: { artworkId: string }) {
  const [data, setData] = useState<EditorData | null>(null);
  const [error, setError] = useState(false);

  useEffect(() => {
    let active = true;
    Promise.all([
      apiFetch<ArtworkDetail>(`/api/artworks/${artworkId}`),
      apiFetch<DescriptionResponse[]>(`/api/artworks/${artworkId}/descriptions`),
    ]).then(([artwork, descriptions]) => {
      if (active) setData({ artwork, descriptions });
    }).catch(() => {
      if (active) setError(true);
    });
    return () => { active = false; };
  }, [artworkId]);

  if (error) return <p role="alert">The artwork editor could not be loaded. Return to the artwork list and try again.</p>;
  if (!data) return <p role="status">Loading artwork editor</p>;

  return (
    <div>
      <a href="/">Back to artworks</a>
      <header className="editor-header">
        <h1>Edit {data.artwork.title}</h1>
        <p>{data.artwork.credit}</p>
        {data.artwork.context && <p>{data.artwork.context}</p>}
      </header>
      <DescriptionEditor artworkId={artworkId} artworkVersion={data.artwork.version}
        artworkTitle={data.artwork.title} initialDescriptions={data.descriptions} />
    </div>
  );
}
