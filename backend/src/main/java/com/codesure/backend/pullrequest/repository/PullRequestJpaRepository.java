package com.codesure.backend.pullrequest.repository;

import com.codesure.backend.pullrequest.entity.PullRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PullRequestJpaRepository extends JpaRepository<PullRequest, UUID> {
    Optional<PullRequest> findByRepositoryIdAndPrNumber(UUID repositoryId, Integer prNumber);
    List<PullRequest> findByRepositoryIdOrderByPrNumberDesc(UUID repositoryId);
}
