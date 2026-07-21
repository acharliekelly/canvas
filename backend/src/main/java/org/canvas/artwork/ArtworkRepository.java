package org.canvas.artwork;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ArtworkRepository extends JpaRepository<Artwork, UUID> {
    List<Artwork> findAllByOrderByCreatedAtDesc();
}
