package me.acharliekelly.canvas.description;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DescriptionRepository extends JpaRepository<Description, UUID> {
    List<Description> findAllByArtworkIdOrderByDisplayOrderAsc(UUID artworkId);

    Optional<Description> findByIdAndArtworkId(UUID id, UUID artworkId);

    @Query("select coalesce(max(d.displayOrder), -1) from Description d where d.artwork.id = :artworkId")
    int maximumDisplayOrder(@Param("artworkId") UUID artworkId);

    @Query("select r from DescriptionRevision r where r.id = :revisionId")
    DescriptionRevision findRevision(@Param("revisionId") UUID revisionId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Description d set d.displayOrder = d.displayOrder + 1000000 where d.artwork.id = :artworkId")
    int moveOrdersOutOfTheWay(@Param("artworkId") UUID artworkId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Description d set d.displayOrder = :displayOrder where d.id = :descriptionId and d.artwork.id = :artworkId")
    int setDisplayOrder(@Param("artworkId") UUID artworkId, @Param("descriptionId") UUID descriptionId,
            @Param("displayOrder") int displayOrder);
}
