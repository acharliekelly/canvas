package org.canvas.artwork;

import java.util.List;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ArtworkRepository extends JpaRepository<Artwork, UUID> {
    List<Artwork> findAllByOrderByCreatedAtDesc();

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Artwork a set a.version = a.version + 1, a.updatedAt = :updatedAt "
            + "where a.id = :artworkId and a.version = :version")
    int advanceVersion(@Param("artworkId") UUID artworkId, @Param("version") long version,
            @Param("updatedAt") Instant updatedAt);
}
