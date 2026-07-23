package me.acharliekelly.canvas.artwork;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ArtworkRepository extends JpaRepository<Artwork, UUID> {
    List<Artwork> findAllByOrderByCreatedAtDesc();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Artwork a where a.id = :artworkId")
    Optional<Artwork> findByIdForUpdate(@Param("artworkId") UUID artworkId);
}
