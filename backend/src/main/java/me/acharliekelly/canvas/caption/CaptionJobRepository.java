package me.acharliekelly.canvas.caption;

import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CaptionJobRepository extends JpaRepository<CaptionJob, UUID> {
    Optional<CaptionJob> findTopByArtworkIdOrderByAttemptCountDesc(UUID artworkId);

    Optional<CaptionJob> findByIdAndArtworkId(UUID id, UUID artworkId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select j from CaptionJob j join fetch j.artwork where j.id = :jobId")
    Optional<CaptionJob> findByIdForUpdate(@Param("jobId") UUID jobId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select j from CaptionJob j where j.state in :states")
    List<CaptionJob> findAllByStateInForUpdate(@Param("states") Collection<CaptionJob.State> states);
}
