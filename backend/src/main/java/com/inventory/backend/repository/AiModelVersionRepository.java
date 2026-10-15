package com.inventory.backend.repository;

import com.inventory.backend.model.AiModelVersion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface AiModelVersionRepository extends JpaRepository<AiModelVersion, Long> {

    Optional<AiModelVersion> findFirstByModelKeyAndActiveTrueOrderByVersionDesc(String modelKey);

    /** Spring Data strips the {@code First} keyword, so both names resolve. */
    default Optional<AiModelVersion> findByModelKeyAndActiveTrueOrderByVersionDesc(String modelKey) {
        return findFirstByModelKeyAndActiveTrueOrderByVersionDesc(modelKey);
    }

    List<AiModelVersion> findByModelKeyOrderByVersionDesc(String modelKey);

    List<AiModelVersion> findByActiveTrueOrderByTrainedAtDesc();

    /** Next version number for a key, so versions are monotonic per model. */
    @Query("SELECT COALESCE(MAX(v.version), 0) FROM AiModelVersion v WHERE v.modelKey = :key")
    int maxVersionForKey(@Param("key") String key);
}