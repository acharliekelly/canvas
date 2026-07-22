package org.canvas.publication;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PublicationRepository extends JpaRepository<Publication, UUID> {
    Optional<Publication> findByArtworkIdAndContentHash(UUID artworkId, String contentHash);

    long countByArtworkId(UUID artworkId);

    @Query("select coalesce(max(p.publicationVersion), 0) from Publication p where p.artwork.id = :artworkId")
    int maximumVersion(@Param("artworkId") UUID artworkId);

    @Query("select distinct p from Publication p left join fetch p.descriptions "
            + "where p.currentArtworkId = :artworkId")
    Optional<Publication> findCurrentByArtworkId(@Param("artworkId") UUID artworkId);

    @Query("select distinct p from Publication p join p.artwork a left join fetch p.descriptions "
            + "where a.publicSlug = :slug and p.currentArtworkId = a.id")
    Optional<Publication> findCurrentBySlug(@Param("slug") String slug);
}
