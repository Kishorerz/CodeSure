package com.codesure.backend.finding.repository;

import com.codesure.backend.finding.entity.Finding;
import com.codesure.backend.finding.entity.Severity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface FindingJpaRepository extends JpaRepository<Finding, UUID> {
    Optional<Finding> findByCodeChangeIdAndRuleIdAndLineNumber(UUID codeChangeId, String ruleId, Integer lineNumber);
    List<Finding> findByCodeChangeId(UUID codeChangeId);
    List<Finding> findByCodeChangeCommitRepositoryId(UUID repositoryId);
    long countByCodeChangeCommitRepositoryIdAndSeverity(UUID repositoryId, Severity severity);
}
