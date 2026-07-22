import { useEffect, useState } from "react";

import { apiFetch } from "../api/client";
import { ApiError } from "../api/client";
import type { PublicArtworkResponse } from "../api/types";

const MAX_ALT_TEXT_LENGTH = 160;

type ArtworkAvailability = "not-found" | "temporarily-unavailable";

export function PublicArtworkPage({ slug }: { slug: string | null }) {
  const [artwork, setArtwork] = useState<PublicArtworkResponse | null>(null);
  const [unavailable, setUnavailable] = useState<ArtworkAvailability | null>(slug === null ? "not-found" : null);

  useEffect(() => {
    let active = true;
    const previousTitle = document.title;
    if (slug === null) {
      document.title = "Artwork unavailable | CANVAS";
      return () => {
        active = false;
        document.title = previousTitle;
      };
    }
    apiFetch<PublicArtworkResponse>(`/public/artworks/${slug}`)
      .then((loaded) => {
        if (!active) return;
        setArtwork(loaded);
        document.title = `${loaded.title} | CANVAS`;
      })
      .catch((error: unknown) => {
        if (!active) return;
        const state: ArtworkAvailability = error instanceof ApiError && error.status === 404
          ? "not-found" : "temporarily-unavailable";
        setUnavailable(state);
        document.title = state === "not-found"
          ? "Artwork unavailable | CANVAS" : "Artwork temporarily unavailable | CANVAS";
      });
    return () => {
      active = false;
      document.title = previousTitle;
    };
  }, [slug]);

  if (unavailable === "not-found") {
    return <main>
      <h1>Artwork unavailable</h1>
      <p role="alert">This artwork could not be found or is not currently published.</p>
    </main>;
  }
  if (unavailable === "temporarily-unavailable") {
    return <main>
      <h1>Artwork temporarily unavailable</h1>
      <p role="alert">This published artwork is temporarily unavailable. Please try again later.</p>
    </main>;
  }
  if (!artwork) return <main><h1>Artwork</h1><p role="status">Loading published artwork</p></main>;

  return <main className="public-artwork">
    <header>
      <h1>{artwork.title}</h1>
      <p className="artwork-credit">{artwork.credit}</p>
    </header>
    <img src={artwork.imageUrl} alt={imageAlt(artwork)} className="public-artwork-image" />
    <div className="published-descriptions">
      {artwork.descriptions.map((description, index) => <section
        aria-labelledby={`published-description-${index}`}
        key={description.audioUrl ?? `legacy-description-${index}`}>
        <h2 id={`published-description-${index}`}>{description.label}</h2>
        {description.audioUrl && <>
          {/* biome-ignore lint/a11y/useMediaCaption: The complete narration transcript is visible immediately below. */}
          <audio controls preload="none" src={description.audioUrl}
            aria-label={`Listen to ${description.label} description for ${artwork.title}`}>
            Your browser does not support audio playback.
          </audio>
        </>}
        <p>{description.text}</p>
      </section>)}
    </div>
  </main>;
}

function imageAlt(artwork: PublicArtworkResponse) {
  const objective = artwork.descriptions.find((description) =>
    description.label.normalize("NFKC").trim().toLocaleLowerCase() === "objective");
  const text = objective?.text.trim();
  if (text && text.length <= MAX_ALT_TEXT_LENGTH) return text;
  return `Artwork: ${artwork.title}. Full descriptions follow.`;
}
