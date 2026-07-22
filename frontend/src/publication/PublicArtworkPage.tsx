import { useEffect, useState } from "react";

import { apiFetch } from "../api/client";
import type { PublicArtworkResponse } from "../api/types";

const MAX_ALT_TEXT_LENGTH = 160;

export function PublicArtworkPage({ slug }: { slug: string }) {
  const [artwork, setArtwork] = useState<PublicArtworkResponse | null>(null);
  const [unavailable, setUnavailable] = useState(false);

  useEffect(() => {
    let active = true;
    const previousTitle = document.title;
    apiFetch<PublicArtworkResponse>(`/public/artworks/${slug}`)
      .then((loaded) => {
        if (!active) return;
        setArtwork(loaded);
        document.title = `${loaded.title} | CANVAS`;
      })
      .catch(() => {
        if (!active) return;
        setUnavailable(true);
        document.title = "Artwork unavailable | CANVAS";
      });
    return () => {
      active = false;
      document.title = previousTitle;
    };
  }, [slug]);

  if (unavailable) {
    return <main>
      <h1>Artwork unavailable</h1>
      <p role="alert">This artwork could not be found or is not currently published.</p>
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
        aria-labelledby={`published-description-${index}`} key={`${index}-${description.label}`}>
        <h2 id={`published-description-${index}`}>{description.label}</h2>
        <p>{description.text}</p>
      </section>)}
    </div>
  </main>;
}

function imageAlt(artwork: PublicArtworkResponse) {
  const objective = artwork.descriptions.find((description) =>
    description.label.toLocaleLowerCase().includes("objective"));
  const text = objective?.text.trim();
  if (text && text.length <= MAX_ALT_TEXT_LENGTH) return text;
  return `Artwork: ${artwork.title}. Full descriptions follow.`;
}
