package me.acharliekelly.canvas.publication.asset;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GeneratedAssetRepository extends JpaRepository<GeneratedAsset, UUID> {
    Optional<GeneratedAsset> findByKindAndInputKey(AssetKind kind, String inputKey);
}
