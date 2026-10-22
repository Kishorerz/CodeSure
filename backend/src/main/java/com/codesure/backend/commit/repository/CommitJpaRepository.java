package com.codesure.backend.commit.repository;

import com.codesure.backend.commit.entity.Commit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CommitJpaRepository extends JpaRepository<Commit, UUID> {
    Optional<Commit> findByRepositoryIdAndSha(UUID repositoryId, String sha);
    List<Commit> findByRepositoryIdOrderByCommittedAtDesc(UUID repositoryId);

    @Query("SELECT DISTINCT c FROM Commit c JOIN c.branches b WHERE c.repository.id = :repoId AND b = :branch ORDER BY c.committedAt DESC")
    List<Commit> findByRepositoryIdAndBranch(@Param("repoId") UUID repoId, @Param("branch") String branch);

    @Query("SELECT DISTINCT b FROM Commit c JOIN c.branches b WHERE c.repository.id = :repoId")
    List<String> findDistinctBranchesByRepositoryId(@Param("repoId") UUID repoId);
}
