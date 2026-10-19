package com.codesure.backend.commit.repository;

import com.codesure.backend.commit.entity.Commit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CommitJpaRepository extends JpaRepository<Commit, UUID> {
    Optional<Commit> findByRepositoryIdAndSha(UUID repositoryId, String sha);
    List<Commit> findByRepositoryIdOrderByCommittedAtDesc(UUID repositoryId);
}
